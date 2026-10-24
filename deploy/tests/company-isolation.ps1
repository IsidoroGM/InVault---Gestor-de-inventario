#requires -Version 7.3
[CmdletBinding()]
param(
    [string]$ComposeExecutable = "docker",
    [switch]$StandaloneCompose
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$generator = Join-Path $projectRoot "deploy/new-company.ps1"
$compose = Join-Path $projectRoot "compose.prod.yml"
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "invault-company-check-$([Guid]::NewGuid().ToString('N'))"
[void][IO.Directory]::CreateDirectory($testRoot)

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw $Message
    }
}

function Assert-Rejected {
    param([scriptblock]$Operation, [string]$Message)
    $rejected = $false
    try {
        & $Operation | Out-Null
    } catch {
        $rejected = $true
    }
    Assert-True $rejected $Message
}

function Read-Environment {
    param([string]$Path)
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        if ($line -match '^([A-Z_]+)=(.*)$') {
            $value = $Matches[2]
            if ($value.StartsWith("'") -and $value.EndsWith("'")) {
                $value = $value.Substring(1, $value.Length - 2).Replace("\'", "'")
            }
            $values[$Matches[1]] = $value
        }
    }
    return $values
}

function Read-ComposeModel {
    param($Company)
    $arguments = @(
        "--project-name", $Company.ProjectName,
        "--env-file", $Company.EnvironmentFile,
        "--file", $compose,
        "config", "--format", "json"
    )
    if ($StandaloneCompose) {
        $json = & $ComposeExecutable @arguments
    } else {
        $json = & $ComposeExecutable compose @arguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Compose no pudo validar la configuración de la empresa de prueba."
    }
    return (($json -join [Environment]::NewLine) | ConvertFrom-Json -AsHashtable)
}

try {
    $first = & $generator -CompanyId "check-a" -Domain "a.example.test" -AdminEmail "admin@a.example.test" -OutputDirectory $testRoot
    $second = & $generator -CompanyId "check-b" -Domain "b.example.test" -AdminEmail "admin@b.example.test" -OutputDirectory $testRoot
    $firstValues = Read-Environment $first.EnvironmentFile
    $secondValues = Read-Environment $second.EnvironmentFile

    foreach ($key in @("COMPOSE_PROJECT_NAME", "DB_NAME", "DB_USERNAME", "DB_PASSWORD", "DB_ROOT_PASSWORD", "INVAULT_JWT_SECRET", "INVAULT_JWT_ISSUER")) {
        Assert-True ($firstValues[$key] -ne $secondValues[$key]) "Las dos empresas comparten $key."
    }
    Assert-True ([Convert]::FromBase64String($firstValues.INVAULT_JWT_SECRET).Length -ge 32) "La clave JWT es demasiado corta."
    Assert-True ($firstValues.DB_PASSWORD -ne $firstValues.DB_ROOT_PASSWORD) "El usuario de aplicación comparte la clave de root."
    Assert-True ($firstValues.INVAULT_BOOTSTRAP_ADMIN_PASSWORD.Length -ge 12 -and $firstValues.INVAULT_BOOTSTRAP_ADMIN_PASSWORD.Length -le 72) "La clave temporal no cumple la longitud del backend."
    Assert-True ($first.PSObject.Properties.Name -notcontains "DB_PASSWORD") "La salida del generador expone contraseñas."

    $original = [IO.File]::ReadAllText($first.EnvironmentFile)
    Assert-Rejected { & $generator -CompanyId "check-a" -Domain "a.example.test" -AdminEmail "admin@a.example.test" -OutputDirectory $testRoot } "Se sobrescribieron las credenciales existentes."
    Assert-True ([IO.File]::ReadAllText($first.EnvironmentFile) -ceq $original) "Las credenciales existentes han cambiado."
    Assert-Rejected { & $generator -CompanyId "../other" -Domain "a.example.test" -AdminEmail "admin@a.example.test" -OutputDirectory $testRoot } "Se aceptó un identificador con recorrido de ruta."
    Assert-Rejected { & $generator -CompanyId "preview" -Domain "a.example.test" -AdminEmail "admin@a.example.test" -OutputDirectory $testRoot } "Se aceptó el nombre reservado del preview."
    Assert-Rejected { & $generator -CompanyId "invalid" -Domain "a.example.test/path" -AdminEmail "admin@a.example.test" -OutputDirectory $testRoot } "Se aceptó un dominio con una ruta."

    $firstModel = Read-ComposeModel $first
    $secondModel = Read-ComposeModel $second
    Assert-True ($firstModel.volumes["mysql-data"].name -ne $secondModel.volumes["mysql-data"].name) "Las empresas comparten el volumen de MySQL."
    foreach ($network in @("data", "edge")) {
        Assert-True ($firstModel.networks[$network].name -ne $secondModel.networks[$network].name) "Las empresas comparten una red Docker."
    }
    foreach ($entry in @(@{ Company = $first; Values = $firstValues; Model = $firstModel }, @{ Company = $second; Values = $secondValues; Model = $secondModel })) {
        $model = $entry.Model
        $values = $entry.Values
        Assert-True ($model.name -eq $entry.Company.ProjectName) "El proyecto efectivo no corresponde a la empresa."
        Assert-True ($model.services.database.environment.MYSQL_DATABASE -eq $values.DB_NAME) "MySQL usa una base distinta a la configurada."
        Assert-True ($model.services.backend.environment.DB_NAME -eq $values.DB_NAME) "El backend no apunta a la base de su empresa."
        Assert-True ($model.services.backend.environment.DB_PASSWORD -ceq $values.DB_PASSWORD) "El backend usa credenciales de otra empresa."
        Assert-True ($model.services.backend.environment.INVAULT_JWT_SECRET -ceq $values.INVAULT_JWT_SECRET) "La clave JWT efectiva no corresponde a la empresa."
        Assert-True ($model.services.backend.environment.INVAULT_CORS_ALLOWED_ORIGINS -eq $entry.Company.PublicUrl) "El origen CORS no corresponde a la empresa."
        foreach ($service in @("database", "backend", "frontend")) {
            Assert-True (-not $model.services[$service].Contains("ports")) "Un servicio privado publica puertos del host."
        }
    }

    # A valid email can contain both an apostrophe and a dollar sign. Dotenv must preserve it.
    $literalEmail = 'o''brien+$company@literal.example.test'
    $literal = & $generator -CompanyId "literal" -Domain "literal.example.test" -AdminEmail $literalEmail -OutputDirectory $testRoot
    $literalModel = Read-ComposeModel $literal
    # Compose config escapes literal dollars as $$ so its output remains reusable as Compose input.
    $serializedEmail = $literalModel.services.backend.environment.INVAULT_BOOTSTRAP_ADMIN_EMAIL
    Assert-True ($serializedEmail.Replace('$$', '$') -ceq $literalEmail) "Dotenv ha interpolado o alterado el correo del administrador."

    Write-Host "Configuración de dos empresas verificada: bases, volúmenes, redes, credenciales y JWT separados; rechazo de sobrescritura y entradas inválidas."
} finally {
    $resolvedTestRoot = [IO.Path]::GetFullPath($testRoot)
    $tempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTestRoot.StartsWith($tempPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($resolvedTestRoot) -notmatch '^invault-company-check-[a-f0-9]{32}$') {
        throw "La carpeta temporal no está dentro del directorio de pruebas permitido."
    }
    Remove-Item -LiteralPath $resolvedTestRoot -Recurse -Force
}
