#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateLength(2, 24)]
    [string]$CompanyId,

    [Parameter(Mandatory)]
    [string]$Domain,

    [Parameter(Mandatory)]
    [string]$AdminEmail,

    [string]$BindAddress = "0.0.0.0",

    [string]$OutputDirectory = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($CompanyId -cnotmatch '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$') {
    throw "CompanyId debe contener letras minúsculas, números y guiones, y comenzar por una letra."
}
if ($CompanyId -eq "preview") {
    throw "El identificador preview está reservado para el entorno de pruebas."
}

$Domain = $Domain.ToLowerInvariant()
$domainPattern = '^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$'
if ($Domain -cnotmatch $domainPattern) {
    throw "Domain debe ser un nombre DNS completo, sin protocolo, puerto ni ruta."
}

$mailAddress = [System.Net.Mail.MailAddress]::new($AdminEmail)
if ($AdminEmail -match '[\r\n]' -or $mailAddress.Address -cne $AdminEmail) {
    throw "AdminEmail debe ser una dirección de correo, sin nombre visible ni saltos de línea."
}

$parsedAddress = $null
if (-not [Net.IPAddress]::TryParse($BindAddress, [ref]$parsedAddress) -or
    $parsedAddress.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) {
    throw "BindAddress debe ser una dirección IPv4 del servidor."
}
$BindAddress = $parsedAddress.ToString()

$projectRoot = Split-Path -Parent $PSScriptRoot
$templatePath = Join-Path $projectRoot ".env.production.example"
$outputRoot = (Resolve-Path -LiteralPath $OutputDirectory).Path
$outputPath = Join-Path $outputRoot ".env.company-$CompanyId"
$projectName = "invault-$CompanyId"
$databaseSuffix = $CompanyId.Replace("-", "_")
$origin = "https://$Domain"

function New-RandomValue {
    param([switch]$Base64)

    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $value = [Convert]::ToBase64String($bytes)
    if ($Base64) {
        return $value
    }
    return $value.TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

$values = [ordered]@{
    COMPOSE_PROJECT_NAME = $projectName
    APP_PUBLIC_URL = $origin
    APP_DOMAIN = $Domain
    HTTPS_BIND_ADDRESS = $BindAddress
    DB_NAME = "invault_$databaseSuffix"
    DB_USERNAME = "iv_$databaseSuffix"
    DB_PASSWORD = New-RandomValue
    DB_ROOT_PASSWORD = New-RandomValue
    INVAULT_JWT_SECRET = New-RandomValue -Base64
    INVAULT_JWT_ISSUER = $origin
    INVAULT_BOOTSTRAP_ADMIN_ENABLED = "true"
    INVAULT_BOOTSTRAP_ADMIN_USERNAME = "admin"
    INVAULT_BOOTSTRAP_ADMIN_EMAIL = $AdminEmail
    INVAULT_BOOTSTRAP_ADMIN_PASSWORD = "Iv-$(New-RandomValue)"
}

$seenKeys = [Collections.Generic.HashSet[string]]::new()
$lines = @("# Private configuration for company $CompanyId. Keep this file outside version control.")
$lines += foreach ($line in [IO.File]::ReadAllLines($templatePath)) {
    if ($line -match '^([A-Z_]+)=') {
        $key = $Matches[1]
        if ($values.Contains($key)) {
            [void]$seenKeys.Add($key)
            # Single quotes keep valid email characters such as $ literal in Compose dotenv.
            $literal = ([string]$values[$key]).Replace("'", "\'")
            "$key='$literal'"
        } else {
            $line
        }
    } else {
        $line
    }
}
foreach ($key in $values.Keys) {
    if (-not $seenKeys.Contains($key)) {
        throw "La plantilla de producción no contiene la variable requerida $key."
    }
}

# CreateNew refuses to overwrite an existing company's credentials.
$options = [IO.FileStreamOptions]::new()
$options.Mode = [IO.FileMode]::CreateNew
$options.Access = [IO.FileAccess]::Write
$options.Share = [IO.FileShare]::None
if (-not $IsWindows) {
    $options.UnixCreateMode = [IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite
}
$stream = [IO.FileStream]::new($outputPath, $options)
try {
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try {
        foreach ($line in $lines) {
            $writer.WriteLine($line)
        }
    } finally {
        $writer.Dispose()
    }
} finally {
    $stream.Dispose()
}

# Return only non-secret metadata. Read the temporary password from the protected file.
[PSCustomObject]@{
    CompanyId = $CompanyId
    ProjectName = $projectName
    EnvironmentFile = $outputPath
    PublicUrl = $origin
}
