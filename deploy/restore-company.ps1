#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$CompanyId,
    [Parameter(Mandatory)][string]$BackupFile,
    [Parameter(Mandatory)][string]$IdentityFile,
    [string]$AgeExecutable = 'age',
    [string]$DockerExecutable = 'docker'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Backup.psm1') -Force
$recovery = @{Container=$null}
$recoveryId = [guid]::NewGuid().ToString('N')
$name = "invault-recovery-$CompanyId-$recoveryId"
$password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
try {
    Invoke-InVaultBackupRestore -BackupFile $BackupFile -CompanyId $CompanyId -IdentityFile $IdentityFile -AgeExecutable $AgeExecutable -RestoreAction {
        param($manifest, $sql)
        # A new container, no published ports, no network and a fresh anonymous MySQL volume.
        $recovery.Container = (Invoke-BackupCommand @{File=$DockerExecutable; Arguments=@('run', '--detach', '--name', $name,
            '--network', 'none', '--label', "io.invault.recovery=$recoveryId", '--label', "io.invault.company=$CompanyId",
            '--env', 'MYSQL_ROOT_PASSWORD', '--env', "MYSQL_DATABASE=$($manifest.databaseName)", 'mysql:8.4.11');
            Environment=@{MYSQL_ROOT_PASSWORD=$password}}).Trim()
        if ($recovery.Container -notmatch '^[a-f0-9]{64}$') { throw 'Unexpected recovery container ID.' }
        $ready = $false
        $probe = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names --execute="SELECT 1"'
        for ($attempt = 0; $attempt -lt 90; $attempt++) {
            try {
                $answer = Invoke-BackupCommand @{File=$DockerExecutable; Arguments=@('exec', $recovery.Container, 'sh', '-c', $probe)}
                if ($answer.Trim() -eq '1') { $ready = $true; break }
            } catch { Start-Sleep -Seconds 1 }
        }
        if (-not $ready) { throw 'Recovery database did not become ready.' }
        $import = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --binary-mode --default-character-set=utf8mb4 "$MYSQL_DATABASE"'
        Write-BackupSql -Sql $sql -Command @{File=$DockerExecutable; Arguments=@('exec', '--interactive', $recovery.Container, 'sh', '-c', $import)}
        [pscustomobject]@{CompanyId=$CompanyId; RecoveryContainer=$name; ContainerId=$recovery.Container;
            Database=$manifest.databaseName; SourceCommit=$manifest.releaseCommit; BackupCreatedUtc=$manifest.createdUtc}
    }
} catch {
    if ($recovery.Container -and $recovery.Container -match '^[a-f0-9]{64}$') {
        $label = (Invoke-BackupCommand @{File=$DockerExecutable; Arguments=@('inspect', '--format', '{{ index .Config.Labels "io.invault.recovery" }}', $recovery.Container)}).Trim()
        if ($label -ceq $recoveryId) { Invoke-BackupCommand @{File=$DockerExecutable; Arguments=@('rm', '--force', '--volumes', $recovery.Container)} | Out-Null }
    }
    throw
}
