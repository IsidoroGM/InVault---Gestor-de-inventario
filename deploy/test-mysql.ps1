#Requires -Version 7.3
<#
.SYNOPSIS
Runs backend verification against a disposable, loopback-only MySQL 8.4 instance on Windows.
.EXAMPLE
./deploy/test-mysql.ps1 -MySqlHome 'C:\tools\mysql-8.4.13-winx64'
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $MySqlHome,
    [string] $AgeDirectory
)

$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'This local runner supports Windows. Use the documented Docker command on Linux/macOS.' }
$repository = Split-Path $PSScriptRoot -Parent
$mysqlDirectory = (Resolve-Path -LiteralPath $MySqlHome).Path
$mysqld = Join-Path $mysqlDirectory 'bin/mysqld.exe'
$mysql = Join-Path $mysqlDirectory 'bin/mysql.exe'
$mysqladmin = Join-Path $mysqlDirectory 'bin/mysqladmin.exe'
foreach ($binary in @($mysqld, $mysql, $mysqladmin)) {
    if (-not (Test-Path -LiteralPath $binary -PathType Leaf)) { throw "Missing MySQL binary: $binary" }
}
$version = & $mysqld --no-defaults --version
if ($LASTEXITCODE -ne 0 -or $version -notmatch 'Ver 8\.4\.') { throw 'Use an official MySQL 8.4 LTS distribution.' }

$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$runDirectory = Join-Path $temporaryRoot ('invault-mysql-it-' + [guid]::NewGuid().ToString('N'))
$dataDirectory = Join-Path $runDirectory 'data'
$initFile = Join-Path $runDirectory 'initialize.sql'
$clientFile = Join-Path $runDirectory 'client.cnf'
$serverLog = Join-Path $runDirectory 'server.log'
$initLog = Join-Path $runDirectory 'initialize.log'
$reports = Join-Path $repository 'target/mysql-it-server'
$serverProcess = $null
$ready = $false
$previousEnvironment = @{}
$environmentKeys = @('INVAULT_TEST_MYSQL_HOST', 'INVAULT_TEST_MYSQL_PORT',
    'INVAULT_TEST_MYSQL_USERNAME', 'INVAULT_TEST_MYSQL_PASSWORD')
foreach ($key in $environmentKeys) {
    $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}

try {
    New-Item -ItemType Directory -Path $runDirectory | Out-Null
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $port = $listener.LocalEndpoint.Port
    $listener.Stop()
    $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    Set-Content -LiteralPath $initFile -Encoding ascii -Value "ALTER USER 'root'@'localhost' IDENTIFIED BY '$password';"
    Set-Content -LiteralPath $clientFile -Encoding ascii -Value @"
[client]
user=root
password=$password
host=127.0.0.1
port=$port
protocol=TCP
"@
    Write-Host "Initializing disposable $version on 127.0.0.1:$port"
    & $mysqld --no-defaults --initialize-insecure "--basedir=$mysqlDirectory" "--datadir=$dataDirectory" "--log-error=$initLog"
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed. See $initLog" }

    $arguments = @('--no-defaults', "--basedir=`"$mysqlDirectory`"", "--datadir=`"$dataDirectory`"",
        '--bind-address=127.0.0.1', "--port=$port", '--mysqlx=OFF', '--skip-log-bin',
        '--innodb-buffer-pool-size=64M', "--init-file=`"$initFile`"", "--log-error=`"$serverLog`"")
    $serverProcess = Start-Process -FilePath $mysqld -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($serverProcess.HasExited) { throw "Disposable MySQL exited before readiness. See $serverLog" }
        $answer = & $mysql "--defaults-extra-file=$clientFile" --connect-timeout=2 --batch --skip-column-names --execute='SELECT 1' 2>$null
        if ($LASTEXITCODE -eq 0 -and $answer -eq '1') { $ready = $true; break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw "Disposable MySQL did not become ready. See $serverLog" }
    Remove-Item -LiteralPath $initFile
    [Environment]::SetEnvironmentVariable('INVAULT_TEST_MYSQL_HOST', '127.0.0.1', 'Process')
    [Environment]::SetEnvironmentVariable('INVAULT_TEST_MYSQL_PORT', [string]$port, 'Process')
    [Environment]::SetEnvironmentVariable('INVAULT_TEST_MYSQL_USERNAME', 'root', 'Process')
    [Environment]::SetEnvironmentVariable('INVAULT_TEST_MYSQL_PASSWORD', $password, 'Process')
    Push-Location $repository
    try {
        & ./mvnw.cmd --batch-mode --no-transfer-progress clean verify -Pmysql-it
        if ($LASTEXITCODE -ne 0) { throw "Backend verification failed (exit $LASTEXITCODE). See target/*-reports." }
        if ($AgeDirectory) {
            & (Join-Path $PSScriptRoot 'tests/backup-recovery.ps1') -MySqlHome $mysqlDirectory -AgeDirectory $AgeDirectory
        }
        $leftovers = & $mysql "--defaults-extra-file=$clientFile" --batch --skip-column-names --execute="SELECT COUNT(*) FROM information_schema.schemata WHERE SCHEMA_NAME LIKE 'invault!_it!_%' ESCAPE '!'; SELECT COUNT(*) FROM mysql.user WHERE User LIKE 'iv!_it!_%' ESCAPE '!'"
        if ($LASTEXITCODE -ne 0 -or ($leftovers | Where-Object { $_ -ne '0' })) {
            throw 'Integration tests left disposable databases or users behind.'
        }
        Write-Host 'Backend verification passed; disposable databases and users were removed.'
    } finally {
        Pop-Location
    }
} finally {
    foreach ($key in $environmentKeys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
    }
    if ($serverProcess -and -not $serverProcess.HasExited) {
        if ($ready) {
            & $mysqladmin "--defaults-extra-file=$clientFile" --connect-timeout=2 shutdown 2>$null
            if (-not $serverProcess.WaitForExit(15000)) { $serverProcess.Kill(); $serverProcess.WaitForExit() }
        } else {
            $serverProcess.Kill()
            $serverProcess.WaitForExit()
        }
    }
    New-Item -ItemType Directory -Path $reports -Force | Out-Null
    foreach ($log in @($initLog, $serverLog)) {
        if (Test-Path -LiteralPath $log) { Copy-Item -LiteralPath $log -Destination $reports -Force }
    }
    # Validate the final absolute target before recursively removing our temporary instance.
    $resolvedRunDirectory = [IO.Path]::GetFullPath($runDirectory)
    $allowedPrefix = Join-Path $temporaryRoot 'invault-mysql-it-'
    if (-not $resolvedRunDirectory.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path $resolvedRunDirectory -Leaf) -notmatch '^invault-mysql-it-[a-f0-9]{32}$') {
        throw "Refusing to remove unexpected temporary directory: $resolvedRunDirectory"
    }
    if (Test-Path -LiteralPath $resolvedRunDirectory) { Remove-Item -LiteralPath $resolvedRunDirectory -Recurse -Force }
}
