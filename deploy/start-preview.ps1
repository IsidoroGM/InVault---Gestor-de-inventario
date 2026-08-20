[CmdletBinding()]
param(
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180,

    [switch]$AcceptPublicExposure
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$EnvironmentFile = Join-Path $ProjectRoot ".env.preview"
$ProductionCompose = Join-Path $ProjectRoot "compose.prod.yml"
$PreviewCompose = Join-Path $ProjectRoot "compose.preview.yml"
$PlaceholderOrigin = "https://pending.trycloudflare.com"

if (-not $AcceptPublicExposure) {
    throw @"
Este comando publica temporalmente InVault en Internet mediante una URL HTTPS aleatoria.
El preview usa una base de datos Docker separada, pero cualquier dato introducido durante
la prueba será accesible a quien conozca la URL. Si aceptas ese riesgo, vuelve a ejecutar:

  .\deploy\start-preview.ps1 -AcceptPublicExposure
"@
}

function New-Base64Secret {
    param([int]$ByteCount = 32)

    $bytes = [byte[]]::new($ByteCount)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes)
}

function New-Password {
    return "Preview-$([Guid]::NewGuid().ToString('N'))"
}

function Set-EnvironmentValue {
    param(
        [Parameter(Mandatory)] [string]$Name,
        [Parameter(Mandatory)] [string]$Value
    )

    $lines = [System.Collections.Generic.List[string]]::new()
    if (Test-Path -LiteralPath $EnvironmentFile) {
        foreach ($line in [IO.File]::ReadAllLines($EnvironmentFile)) {
            $lines.Add($line)
        }
    }

    $replacement = "$Name=$Value"
    $updated = $false
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match "^$([regex]::Escape($Name))=") {
            $lines[$index] = $replacement
            $updated = $true
            break
        }
    }

    if (-not $updated) {
        $lines.Add($replacement)
    }

    [IO.File]::WriteAllLines($EnvironmentFile, $lines, [Text.UTF8Encoding]::new($false))
}

function Get-EnvironmentValue {
    param([Parameter(Mandatory)] [string]$Name)

    foreach ($line in [IO.File]::ReadAllLines($EnvironmentFile)) {
        if ($line -match "^$([regex]::Escape($Name))=(.*)$") {
            return $Matches[1]
        }
    }

    return $null
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments)] [string[]]$Arguments)

    & docker compose `
        --env-file $EnvironmentFile `
        --file $ProductionCompose `
        --file $PreviewCompose `
        @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose terminó con código $LASTEXITCODE."
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker no está instalado o no está disponible en PATH."
}

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Desktop no está iniciado o el motor Docker no está accesible."
}

if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    $previewPassword = New-Password
    $environmentLines = @(
        "# Local secrets for the disposable HTTPS preview. Never commit this file."
        "APP_PUBLIC_URL=$PlaceholderOrigin"
        "APP_DOMAIN=pending.trycloudflare.com"
        "HTTPS_BIND_ADDRESS=127.0.0.1"
        "PREVIEW_HTTP_PORT=8088"
        "DB_NAME=invault"
        "DB_USERNAME=invault"
        "DB_PASSWORD=$(New-Password)"
        "DB_ROOT_PASSWORD=$(New-Password)"
        "DB_POOL_MAX_SIZE=10"
        "DB_POOL_MIN_IDLE=2"
        "DB_CONNECTION_TIMEOUT_MS=30000"
        "INVAULT_JWT_SECRET=$(New-Base64Secret)"
        "INVAULT_JWT_ISSUER=$PlaceholderOrigin"
        "INVAULT_JWT_EXPIRATION=30m"
        "INVAULT_BOOTSTRAP_ADMIN_ENABLED=true"
        "INVAULT_BOOTSTRAP_ADMIN_USERNAME=admin"
        "INVAULT_BOOTSTRAP_ADMIN_EMAIL=admin@preview.local"
        "INVAULT_BOOTSTRAP_ADMIN_PASSWORD=$previewPassword"
    )
    [IO.File]::WriteAllLines(
        $EnvironmentFile,
        $environmentLines,
        [Text.UTF8Encoding]::new($false)
    )
    Write-Host "Se creó .env.preview con secretos locales aleatorios."
}

Push-Location $ProjectRoot
try {
    Write-Host "Construyendo e iniciando InVault y el túnel HTTPS..."
    Invoke-Compose up --detach --build database backend frontend cloudflared

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $previewUrl = $null
    do {
        $tunnelLogs = (& docker compose `
            --env-file $EnvironmentFile `
            --file $ProductionCompose `
            --file $PreviewCompose `
            logs --no-color cloudflared 2>&1 | Out-String)

        $matches = [regex]::Matches(
            $tunnelLogs,
            'https://[a-z0-9-]+\.trycloudflare\.com',
            [Text.RegularExpressions.RegexOptions]::IgnoreCase
        )
        if ($matches.Count -gt 0) {
            $previewUrl = $matches[$matches.Count - 1].Value.ToLowerInvariant()
            break
        }

        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    if (-not $previewUrl) {
        Invoke-Compose logs --tail 80 cloudflared
        throw "El túnel no publicó una URL en $TimeoutSeconds segundos."
    }

    Set-EnvironmentValue -Name "APP_PUBLIC_URL" -Value $previewUrl
    Set-EnvironmentValue -Name "APP_DOMAIN" -Value ([Uri]$previewUrl).Host
    Set-EnvironmentValue -Name "INVAULT_JWT_ISSUER" -Value $previewUrl

    Write-Host "Aplicando el origen público a CORS, JWT y STOMP..."
    Invoke-Compose up --detach --force-recreate backend

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $backendHealthy = $false
    do {
        $backendContainer = (& docker compose `
            --env-file $EnvironmentFile `
            --file $ProductionCompose `
            --file $PreviewCompose `
            ps --quiet backend).Trim()

        if ($backendContainer) {
            $backendStatus = (& docker inspect `
                --format '{{.State.Health.Status}}' `
                $backendContainer 2>$null).Trim()
            if ($backendStatus -eq "healthy") {
                $backendHealthy = $true
                break
            }
        }

        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    if (-not $backendHealthy) {
        Invoke-Compose logs --tail 80 backend
        throw "El backend no alcanzó el estado healthy antes del límite de tiempo."
    }

    $healthUrl = "$previewUrl/healthz"
    $publicHealthy = $false
    do {
        try {
            $response = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 10 -UseBasicParsing
            if ($response.StatusCode -eq 200) {
                $publicHealthy = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 3
        }
    } while ([DateTimeOffset]::UtcNow -lt $deadline)

    if (-not $publicHealthy) {
        Invoke-Compose ps
        throw "InVault no respondió correctamente en $healthUrl antes del límite de tiempo."
    }

    $adminUsername = Get-EnvironmentValue -Name "INVAULT_BOOTSTRAP_ADMIN_USERNAME"
    $adminPassword = Get-EnvironmentValue -Name "INVAULT_BOOTSTRAP_ADMIN_PASSWORD"

    Write-Host ""
    Write-Host "InVault está disponible mediante HTTPS:" -ForegroundColor Green
    Write-Host $previewUrl -ForegroundColor Cyan
    Write-Host "Usuario inicial: $adminUsername"
    Write-Host "Contraseña inicial guardada en .env.preview: $adminPassword"
    Write-Host "Cambia la contraseña en el primer inicio de sesión."
    Write-Host "La URL existirá mientras Docker y el contenedor cloudflared sigan activos."
} finally {
    Pop-Location
}
