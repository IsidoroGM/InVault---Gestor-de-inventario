#requires -Version 7.3
[CmdletBinding()]
param(
    [ValidatePattern('^invault-backend:(?:latest|[a-f0-9]{40})$')][string]$BackendImage='invault-backend:latest',
    [ValidatePattern('^invault-frontend:(?:latest|[a-f0-9]{40})$')][string]$FrontendImage='invault-frontend:latest',
    [ValidatePattern('^[a-f0-9]{40}$')][string]$ExpectedRevision,
    [string]$AgeDirectory
)
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
            $health = Invoke-WebRequest -Uri "$script:baseUrl/api/health/readiness" -Headers @{Host='localhost'} -TimeoutSec 3 -SkipHttpErrorCheck
            if ($health.StatusCode -eq 200 -and ($health.Content | ConvertFrom-Json).status -eq 'UP') { return }
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
    $overrideSource = @'
services:
  backend:
    image: __BACKEND_IMAGE__
    environment:
      INVAULT_LOGIN_ACCOUNT_ATTEMPTS: "2"
      INVAULT_LOGIN_IP_ATTEMPTS: "4"
      INVAULT_LOGIN_WINDOW: "5m"
      DB_CONNECTION_TIMEOUT_MS: "1000"
  frontend:
    image: __FRONTEND_IMAGE__
  gateway:
    environment:
      APP_DOMAIN: http://localhost
    ports: !override
      - "127.0.0.1::80"
'@
    [IO.File]::WriteAllText($override,$overrideSource.Replace('__BACKEND_IMAGE__',$BackendImage).Replace('__FRONTEND_IMAGE__',$FrontendImage))
    $compose = @('compose', '--project-name', $project, '--env-file', $company.EnvironmentFile,
        '--file', (Join-Path $repository 'compose.prod.yml'), '--file', $override)
    $ownsProject = $true
    Compose-Command -Arguments @('up', '--detach', '--no-build', '--wait', '--wait-timeout', '240') | Out-Null
    $published = (Compose-Command -Arguments @('port', 'gateway', '80')).Trim()
    Assert-True ($published -match '^127\.0\.0\.1:(\d+)$') 'Unexpected published test address.'
    $script:baseUrl = "http://127.0.0.1:$($Matches[1])"
    Wait-Api
    $diagnostics = Join-Path $repository 'deploy/check-company.ps1'
    $shell = (Get-Process -Id $PID).Path
    $revisionArguments = if ($ExpectedRevision) {@('-ExpectedRevision',$ExpectedRevision)} else {@()}
    $statusJson = & $shell -NoProfile -NonInteractive -File $diagnostics -CompanyId "login-$testId" -EnvironmentFile $company.EnvironmentFile @revisionArguments
    Assert-True ($LASTEXITCODE -eq 0) 'The company diagnostic rejected healthy production containers.'
    $companyStatus = ($statusJson -join "`n") | ConvertFrom-Json
    Assert-True ($companyStatus.healthy -and $companyStatus.readinessAvailable) 'The runtime diagnostic did not confirm readiness.'

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
    $backend = (Compose-Command -Arguments @('ps', '--quiet', 'backend')).Trim()
    $started = (Invoke-BackupCommand @{File='docker'; Arguments=@('inspect', '--format', '{{.State.StartedAt}}', $backend)}).Trim()
    Compose-Command -Arguments @('stop', 'database') | Out-Null
    $down = Invoke-WebRequest -Uri "$script:baseUrl/api/health/readiness" -Headers @{Host='localhost'} -TimeoutSec 15 -SkipHttpErrorCheck
    Assert-True ($down.StatusCode -eq 503 -and ($down.Content | ConvertFrom-Json).status -eq 'DOWN') 'Readiness stayed available after stopping MySQL.'
    Assert-True (@(($down.Content | ConvertFrom-Json).PSObject.Properties).Count -eq 1) 'Public readiness exposed internal details.'
    Assert-True ((Invoke-WebRequest -Uri "$script:baseUrl/api/health" -Headers @{Host='localhost'} -TimeoutSec 5).StatusCode -eq 200) 'Database failure incorrectly stopped Java liveness.'
    $downJson = & $shell -NoProfile -NonInteractive -File $diagnostics -CompanyId "login-$testId" -EnvironmentFile $company.EnvironmentFile @revisionArguments
    Assert-True ($LASTEXITCODE -eq 2 -and -not (($downJson -join "`n") | ConvertFrom-Json).healthy) 'The company diagnostic missed a stopped database.'
    Compose-Command -Arguments @('up', '--detach', '--no-build', '--wait', '--wait-timeout', '120', 'database') | Out-Null
    Wait-Api
    $recovered = (Invoke-BackupCommand @{File='docker'; Arguments=@('inspect', '--format', '{{.State.StartedAt}}', $backend)}).Trim()
    Assert-True ($started -ceq $recovered) 'Database recovery unexpectedly restarted the backend.'
    Assert-True ((Login-Request 'after-db-recovery' 7).StatusCode -eq 429) 'Database restart lost the source budget.'
    # A healthy exit at the end also clears the intentionally nonzero native status above.
    # Wait for the next container healthcheck after recovery before evaluating the snapshot.
    Compose-Command -Arguments @('up', '--detach', '--no-build', '--wait', '--wait-timeout', '120') | Out-Null
    $restoredJson = & $shell -NoProfile -NonInteractive -File $diagnostics -CompanyId "login-$testId" -EnvironmentFile $company.EnvironmentFile @revisionArguments
    Assert-True ($LASTEXITCODE -eq 0 -and (($restoredJson -join "`n") | ConvertFrom-Json).healthy) 'The company diagnostic did not recover with the database.'
    if ($ExpectedRevision -and $AgeDirectory) {
        $extension=if ($IsWindows) {'.exe'} else {''}
        $age=Join-Path $AgeDirectory "age$extension"
        $keygen=Join-Path $AgeDirectory "age-keygen$extension"
        $identity=Join-Path $testRoot 'release-test.agekey'
        $recipient=Join-Path $testRoot 'release-recipient.txt'
        Invoke-BackupCommand @{File=$keygen;Arguments=@('--output',$identity)} | Out-Null
        $publicKey=Invoke-BackupCommand @{File=$keygen;Arguments=@('-y',$identity)}
        [IO.File]::WriteAllText($recipient,$publicKey)
        $backup=& (Join-Path $repository 'deploy/backup-company.ps1') -CompanyId "login-$testId" -EnvironmentFile $company.EnvironmentFile -RecipientFile $recipient -OutputDirectory $testRoot -AgeExecutable $age
        Assert-True ($backup.ReleaseCommit -ceq $ExpectedRevision) 'The backup did not use the deployed backend image revision.'
        $manifestProbe=@{matched=$false}
        Invoke-InVaultBackupRestore -BackupFile $backup.BackupFile -CompanyId "login-$testId" -IdentityFile $identity -AgeExecutable $age -RestoreAction {
            param($manifest,$sql)
            $manifestProbe.matched = $manifest.releaseCommit -ceq $ExpectedRevision
        }
        Assert-True $manifestProbe.matched 'The encrypted manifest did not retain the deployed release identity.'
    }
    Write-Host 'Login security passed: production proxy chain, spoofed headers rejected, per-account/IP limits, Retry-After, audit and restart persistence.'
    Write-Host 'Readiness passed: MySQL outage returns 503, Java stays live and database recovery restores availability without a backend restart.'
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
