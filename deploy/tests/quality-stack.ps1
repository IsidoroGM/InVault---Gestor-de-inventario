#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^invault-backend:[a-f0-9]{40}$')][string]$BackendImage,
    [Parameter(Mandatory)][ValidatePattern('^invault-frontend:[a-f0-9]{40}$')][string]$FrontendImage,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$ExpectedRevision,
    [Parameter(Mandatory)][string]$ReportDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repository 'deploy/Backup.psm1') -Force -WarningAction SilentlyContinue
$testId = [guid]::NewGuid().ToString('N').Substring(0,10)
$project = "invault-quality-$testId"
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "invault-quality-test-$testId"
[void][IO.Directory]::CreateDirectory($testRoot)
$keys = @('INVAULT_TEST_URL','INVAULT_TEST_RUN_ID','INVAULT_TEST_MUTATIONS','INVAULT_TEST_PASSWORD','INVAULT_TEST_BOOTSTRAP_PASSWORD','INVAULT_TEST_REVISION','INVAULT_QUALITY_REPORT')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
$compose = $null
$ownsProject = $false
function Compose-Command([string[]]$Arguments) { Invoke-BackupCommand @{File='docker'; Arguments=($compose + $Arguments)} }
try {
    $existing = Invoke-BackupCommand @{File='docker'; Arguments=@('ps','--all','--quiet','--filter',"label=com.docker.compose.project=$project")}
    if (-not [string]::IsNullOrWhiteSpace($existing)) { throw 'The disposable quality project already exists.' }
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $listener.Start()
    try { $port = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
    $baseUrl = "http://localhost:$port"
    $company = & (Join-Path $repository 'deploy/new-company.ps1') -CompanyId "quality-$testId" -Domain "quality-$testId.example.test" -AdminEmail "admin@quality-$testId.example.test" -OutputDirectory $testRoot
    $override = Join-Path $testRoot 'quality.compose.yml'
    $source = @'
services:
  backend:
    image: __BACKEND__
    environment:
      INVAULT_CORS_ALLOWED_ORIGINS: __URL__
      INVAULT_REALTIME_ALLOWED_ORIGINS: __URL__
      INVAULT_LOGIN_ACCOUNT_ATTEMPTS: "1000"
      INVAULT_LOGIN_IP_ATTEMPTS: "2000"
      DB_CONNECTION_TIMEOUT_MS: "1000"
  frontend:
    image: __FRONTEND__
  gateway:
    environment:
      APP_DOMAIN: http://localhost
    ports: !override
      - "127.0.0.1:__PORT__:80"
'@
    [IO.File]::WriteAllText($override,$source.Replace('__BACKEND__',$BackendImage).Replace('__FRONTEND__',$FrontendImage).Replace('__URL__',$baseUrl).Replace('__PORT__',[string]$port))
    $compose = @('compose','--project-name',$project,'--env-file',$company.EnvironmentFile,'--file',(Join-Path $repository 'compose.prod.yml'),'--file',$override)
    $model = Compose-Command @('config','--format','json') | ConvertFrom-Json -AsHashtable
    foreach ($service in @('backend','frontend')) {
        $image = if ($service -eq 'backend') {$BackendImage} else {$FrontendImage}
        $revision = (Invoke-BackupCommand @{File='docker'; Arguments=@('image','inspect','--format','{{index .Config.Labels "org.opencontainers.image.revision"}}',$image)}).Trim()
        if ($revision -cne $ExpectedRevision) { throw 'A quality image has an unexpected revision.' }
    }
    $ownsProject = $true
    Compose-Command @('up','--detach','--no-build','--wait','--wait-timeout','240') | Out-Null
    $published = (Compose-Command @('port','gateway','80')).Trim()
    if ($published -cne "127.0.0.1:$port") { throw 'The quality endpoint is not the owned loopback binding.' }
    $env:INVAULT_TEST_URL = $baseUrl
    $env:INVAULT_TEST_RUN_ID = $testId
    $env:INVAULT_TEST_MUTATIONS = 'owned-disposable-stack'
    $env:INVAULT_TEST_PASSWORD = "Quality-changed-$([guid]::NewGuid().ToString('N'))"
    $env:INVAULT_TEST_BOOTSTRAP_PASSWORD = $model.services.backend.environment.INVAULT_BOOTSTRAP_ADMIN_PASSWORD
    $env:INVAULT_TEST_REVISION = $ExpectedRevision
    $env:INVAULT_QUALITY_REPORT = [IO.Path]::GetFullPath($ReportDirectory)
    [void][IO.Directory]::CreateDirectory($env:INVAULT_QUALITY_REPORT)
    & node (Join-Path $PSScriptRoot 'quality-init.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Quality initialization failed.' }
    & npm --prefix (Join-Path $repository 'frontend') run test:e2e
    if ($LASTEXITCODE -ne 0) { throw 'Browser flows failed; inspect the disposable quality report.' }
    & node (Join-Path $PSScriptRoot 'quality-load.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'The bounded load verification failed.' }
    Write-Host 'Quality stack passed: real browser flows and bounded concurrent workload.'
} finally {
    try {
        if ($ownsProject) { Compose-Command @('down','--volumes','--remove-orphans') | Out-Null }
    } finally {
        foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
        $absolute = [IO.Path]::GetFullPath($testRoot)
        if ((Split-Path $absolute -Parent) -cne ([IO.Path]::GetTempPath().TrimEnd([IO.Path]::DirectorySeparatorChar)) -or (Split-Path $absolute -Leaf) -notmatch '^invault-quality-test-[a-f0-9]{10}$') { throw 'Unsafe quality resource cleanup.' }
        if (Test-Path -LiteralPath $absolute) { Remove-Item -LiteralPath $absolute -Recurse -Force }
    }
}
