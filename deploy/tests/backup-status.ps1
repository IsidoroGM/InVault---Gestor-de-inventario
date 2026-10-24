#requires -Version 7.3
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '../BackupStatus.psm1') -Force
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "invault-backup-status-$([guid]::NewGuid().ToString('N'))"
[void][IO.Directory]::CreateDirectory($testRoot)
$replicaRoot = Join-Path $testRoot 'replica'
[void][IO.Directory]::CreateDirectory($replicaRoot)
$now = [DateTimeOffset]::Parse('2026-10-25T12:00:00Z', [Globalization.CultureInfo]::InvariantCulture)
$cases = 0
function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message }; $script:cases++ }
function New-Probe([string]$Company, [DateTimeOffset]$Created, [string]$Root = $testRoot) {
    $path = Join-Path $Root "$Company-$($Created.UtcDateTime.ToString("yyyyMMdd'T'HHmmssfff'Z'", [Globalization.CultureInfo]::InvariantCulture))-$([guid]::NewGuid().ToString('N')).zip.age"
    # Synthetic header fixtures verify the monitor, not encryption or recoverability.
    [IO.File]::WriteAllText($path, "age-encryption.org/v1`n" + ('x' * 1024), [Text.UTF8Encoding]::new($false))
    return $path
}
function Status { Get-InVaultBackupStatus -CompanyId 'check-a' -BackupDirectory $testRoot -Now $now }
try {
    Assert-True (-not (Status).healthy) 'An empty directory was considered protected.'
    [void](New-Probe 'check-b' $now.AddMinutes(-1))
    Assert-True (-not (Status).healthy) 'Another company counted as a backup.'
    $old = New-Probe 'check-a' $now.AddHours(-31)
    (Get-Item -LiteralPath $old).LastWriteTimeUtc = $now.UtcDateTime
    [IO.File]::WriteAllText((Join-Path $testRoot 'check-a-current.zip.age.part'), 'incomplete')
    Assert-True (-not (Status).healthy) 'A touched old file or partial file reset the backup age.'
    $fresh = New-Probe 'check-a' $now.AddHours(-1)
    Assert-True ((Status).healthy) 'A recent company backup failed structural monitoring.'
    Assert-True ((Status).backupFile -ceq $fresh) 'The latest company timestamp was not selected.'
    $future = New-Probe 'check-a' $now.AddHours(1)
    Assert-True (-not (Status).healthy) 'A future timestamp was accepted.'
    Remove-Item -LiteralPath $future -Force
    $broken = New-Probe 'check-a' $now.AddMinutes(-1)
    [IO.File]::WriteAllText($broken, 'truncated')
    Assert-True (-not (Status).healthy) 'A corrupt latest artifact silently fell back to an older one.'
    Remove-Item -LiteralPath $broken -Force
    $replicaArgs = @{CompanyId='check-a'; BackupDirectory=$testRoot; ReplicaDirectory=$replicaRoot; Now=$now}
    Assert-True (-not (Get-InVaultBackupStatus @replicaArgs).healthy) 'A missing replica was accepted.'
    $replica = Join-Path $replicaRoot ([IO.Path]::GetFileName($fresh))
    [IO.File]::Copy($fresh, $replica)
    Assert-True ((Get-InVaultBackupStatus @replicaArgs).healthy) 'An identical replica was rejected.'
    $bytes = [IO.File]::ReadAllBytes($replica)
    $bytes[$bytes.Length - 1] = $bytes[$bytes.Length - 1] -bxor 1
    [IO.File]::WriteAllBytes($replica, $bytes)
    Assert-True (-not (Get-InVaultBackupStatus @replicaArgs).healthy) 'A same-size modified replica passed the hash check.'
    Assert-True (-not (Get-InVaultBackupStatus -CompanyId 'check-a' -BackupDirectory $testRoot -ReplicaDirectory $testRoot -Now $now).healthy) 'The source directory was accepted as its own replica.'
    Assert-True (-not (Get-InVaultBackupStatus -CompanyId 'check-a' -BackupDirectory (Join-Path $testRoot 'missing') -Now $now).healthy) 'A missing directory was accepted.'
    $rejected = $false
    try { Get-InVaultBackupStatus -CompanyId '../bad' -BackupDirectory $testRoot -Now $now | Out-Null } catch { $rejected = $true }
    Assert-True $rejected 'An invalid company name was accepted.'
    $rejected = $false
    try { Get-InVaultBackupStatus -CompanyId check-a -BackupDirectory $testRoot -ReplicaDirectory '' -Now $now | Out-Null } catch { $rejected = $true }
    Assert-True $rejected 'An explicitly empty replica setting silently disabled verification.'
    # Exercise the command-line exit contract without ending this test process.
    $shell = (Get-Process -Id $PID).Path
    $wrapper = Join-Path $PSScriptRoot '../check-backups.ps1'
    # Keep real-clock CLI fixtures separate from the deterministic timestamp cases above.
    $cliRoot = Join-Path $testRoot 'cli'
    [void][IO.Directory]::CreateDirectory($cliRoot)
    [void](New-Probe 'check-a' ([DateTimeOffset]::UtcNow) $cliRoot)
    & $shell -NoProfile -NonInteractive -File $wrapper -CompanyId check-missing -BackupDirectory $cliRoot | Out-Null
    Assert-True ($LASTEXITCODE -eq 2) 'Unhealthy monitoring did not exit two.'
    & $shell -NoProfile -NonInteractive -File $wrapper -CompanyId check-a -BackupDirectory $cliRoot | Out-Null
    Assert-True ($LASTEXITCODE -eq 0) 'Healthy monitoring did not exit zero.'
    Write-Host "Backup monitoring passed: $cases checks for company identity, freshness, future/partial/truncated files, replica hashes and exit codes."
} finally {
    $absolute = [IO.Path]::GetFullPath($testRoot)
    if ((Split-Path $absolute -Parent) -cne ([IO.Path]::GetTempPath().TrimEnd([IO.Path]::DirectorySeparatorChar)) -or
        (Split-Path $absolute -Leaf) -notmatch '^invault-backup-status-[a-f0-9]{32}$') { throw 'Unsafe monitor test cleanup.' }
    Remove-Item -LiteralPath $absolute -Recurse -Force
}
