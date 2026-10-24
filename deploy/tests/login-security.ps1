#requires -Version 7.3
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repository 'deploy/Backup.psm1') -Force
$testId = [guid]::NewGuid().ToString('N').Substring(0, 10)
$project = "invault-login-$testId"
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "invault-login-test-$testId"
[void][IO.Directory]::CreateDirectory($testRoot)
$compose = $null
$ownsProject = $false

function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Compose-Command([string[]]$Arguments) {
    Invoke-BackupCommand @{File='docker'; Arguments=($compose + $Arguments)}
}
function Wait-Api {
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        try {
            $health = Invoke-WebRequest -Uri "$script:baseUrl/api/health" -Headers @{Host='localhost'} -TimeoutSec 3 -SkipHttpErrorCheck
            if ($health.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Seconds 1
    }
    throw 'The disposable login test API did not become ready.'
}
function Login-Request([string]$Username, [int]$Spoof) {
    # The edge must replace X-Forwarded-* and remove RFC Forwarded before Spring uses them.
    $headers = @{
        Host='localhost'
        Forwarded="for=198.51.100.$Spoof;proto=https;host=spoof.example.test"
        'X-Forwarded-For'="203.0.113.$Spoof"
        'X-Forwarded-Host'='spoof.example.test'
        'X-Forwarded-Proto'='https'
        'X-Forwarded-Port'='9999'
        'X-Forwarded-Prefix'='/spoof'
    }
    $body = @{username=$Username; password='deliberately-invalid-test-password'} | ConvertTo-Json -Compress
    Invoke-WebRequest -Uri "$script:baseUrl/api/auth/login" -Method Post -Headers $headers `
        -ContentType 'application/json' -Body $body -SkipHttpErrorCheck -TimeoutSec 15
}

try {
    $existing = Invoke-BackupCommand @{File='docker'; Arguments=@('ps', '--all', '--quiet', '--filter', "label=com.docker.compose.project=$project")}
    Assert-True ([string]::IsNullOrWhiteSpace($existing)) 'Refusing to reuse a non-empty Compose project.'
    $company = & (Join-Path $repository 'deploy/new-company.ps1') -CompanyId "login-$testId" `
        -Domain "login-$testId.example.test" -AdminEmail "admin@login-$testId.example.test" -OutputDirectory $testRoot
    $override = Join-Path $testRoot 'login.compose.yml'
    # Use the exact images built in Deployment CI. The original production Caddy/nginx files
    # still route every request; an explicit HTTP site avoids public DNS/ACME for this local test.
    [IO.File]::WriteAllText($override, @'
services:
  backend:
    image: invault-backend:latest
    environment:
      INVAULT_LOGIN_ACCOUNT_ATTEMPTS: "2"
      INVAULT_LOGIN_IP_ATTEMPTS: "4"
      INVAULT_LOGIN_WINDOW: "5m"
  frontend:
    image: invault-frontend:latest
  gateway:
    environment:
      APP_DOMAIN: http://localhost
    ports: !override
      - "127.0.0.1::80"
'@)
    $compose = @('compose', '--project-name', $project, '--env-file', $company.EnvironmentFile,
        '--file', (Join-Path $repository 'compose.prod.yml'), '--file', $override)
    $ownsProject = $true
    Compose-Command -Arguments @('up', '--detach', '--no-build', '--wait', '--wait-timeout', '240') | Out-Null
    $published = (Compose-Command -Arguments @('port', 'gateway', '80')).Trim()
    Assert-True ($published -match '^127\.0\.0\.1:(\d+)$') 'Unexpected published test address.'
    $script:baseUrl = "http://127.0.0.1:$($Matches[1])"
    Wait-Api

    Assert-True ((Login-Request 'unknown-operator' 1).StatusCode -eq 401) 'First invalid login was not rejected normally.'
    Assert-True ((Login-Request 'UNKNOWN-OPERATOR' 2).StatusCode -eq 401) 'Case variant did not share the account budget.'
    $limited = Login-Request 'unknown-operator' 3
    Assert-True ($limited.StatusCode -eq 429) 'The account budget was bypassed through the proxy.'
    Assert-True ([int]($limited.Headers['Retry-After'] | Select-Object -First 1) -gt 0) 'Retry-After was lost at the proxy.'
    $payload = $limited.Content | ConvertFrom-Json
    Assert-True ($payload.path -eq '/api/auth/login' -and $payload.retryAfterSeconds -gt 0) 'Forwarded headers altered the request path or error contract.'
    Assert-True ((Login-Request 'another-user' 4).StatusCode -eq 401) 'Another account should fit in the remaining source budget.'
    Assert-True ((Login-Request 'third-user' 5).StatusCode -eq 429) 'Spoofed source headers bypassed the IP budget.'

    $sql = "SELECT COUNT(*) FROM login_rate_limits; SELECT COUNT(*) FROM audit_logs WHERE action='LOGIN_THROTTLED'; SELECT COUNT(*) FROM audit_logs WHERE client_ip LIKE '198.51.100.%' OR client_ip LIKE '203.0.113.%';"
    $check = (Compose-Command -Arguments @('exec', '--no-TTY', 'database', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --batch --skip-column-names --execute="$1"', 'mysql', $sql)).Trim() -split '\r?\n'
    Assert-True (($check -join ',') -eq '3,2,0') 'Counter/audit state or trusted client address was incorrect.'
    Compose-Command -Arguments @('restart', 'backend') | Out-Null
    Wait-Api
    Assert-True ((Login-Request 'after-restart' 6).StatusCode -eq 429) 'Restarting the backend reset the persisted budget.'
    Write-Host 'Login security passed: production proxy chain, spoofed headers rejected, per-account/IP limits, Retry-After, audit and restart persistence.'
} finally {
    if ($ownsProject -and $compose) {
        if ($project -cnotmatch '^invault-login-[a-f0-9]{10}$') { throw 'Unsafe login test project cleanup.' }
        Compose-Command -Arguments @('down', '--volumes', '--remove-orphans') | Out-Null
    }
    $absolute = [IO.Path]::GetFullPath($testRoot)
    if ((Split-Path $absolute -Parent) -cne ([IO.Path]::GetTempPath().TrimEnd([IO.Path]::DirectorySeparatorChar)) -or
        (Split-Path $absolute -Leaf) -notmatch '^invault-login-test-[a-f0-9]{10}$') { throw 'Unsafe login test directory cleanup.' }
    Remove-Item -LiteralPath $absolute -Recurse -Force
}
