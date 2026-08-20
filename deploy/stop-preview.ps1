[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$EnvironmentFile = Join-Path $ProjectRoot ".env.preview"

if (-not (Test-Path -LiteralPath $EnvironmentFile)) {
    throw "No existe .env.preview; no hay un entorno de pruebas configurado."
}

Push-Location $ProjectRoot
try {
    & docker compose `
        --env-file $EnvironmentFile `
        --file compose.prod.yml `
        --file compose.preview.yml `
        down

    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo detener el entorno de pruebas."
    }

    Write-Host "Entorno detenido. El volumen de MySQL y .env.preview se han conservado."
} finally {
    Pop-Location
}

