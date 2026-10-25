#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$CompanyId,
    [Parameter(Mandatory)][string]$EnvironmentFile,
    [Parameter(Mandatory)][string]$RecipientFile,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [string]$ReleaseCommit,
    [string]$ReplicaDirectory,
    [string]$AgeExecutable = 'age',
    [string]$DockerExecutable = 'docker'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Backup.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'ReleaseIdentity.psm1') -Force
Assert-BackupCompany $CompanyId
$repository = Split-Path $PSScriptRoot -Parent
$project = "invault-$CompanyId"
$database = 'invault_' + $CompanyId.Replace('-', '_')
$environmentPath = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$composeArguments = @('compose', '--env-file', $environmentPath, '--file', (Join-Path $repository 'compose.prod.yml'))
$model = Invoke-BackupCommand @{File=$DockerExecutable; Arguments=($composeArguments + @('config', '--format', 'json'))} | ConvertFrom-Json
if ($model.name -cne $project -or $model.services.database.environment.MYSQL_DATABASE -cne $database) {
    throw 'The environment file does not select the requested company project/database.'
}
$container = (Invoke-BackupCommand @{File=$DockerExecutable; Arguments=($composeArguments + @('--project-name', $project, 'ps', '--quiet', 'database'))}).Trim()
if ($container -notmatch '^[a-f0-9]{12,64}$') { throw 'Expected exactly one running company database container.' }
$inspection = (Invoke-BackupCommand @{File=$DockerExecutable; Arguments=@('inspect', $container)} | ConvertFrom-Json)[0]
if ($inspection.Config.Labels.'com.docker.compose.project' -cne $project -or
    $inspection.Config.Labels.'com.docker.compose.service' -cne 'database' -or
    $inspection.Config.Env -cnotcontains "MYSQL_DATABASE=$database" -or -not $inspection.State.Running) {
    throw 'The database container identity does not match the requested company.'
}
$recordedRevision = $null
$backend = (Invoke-BackupCommand @{File=$DockerExecutable; Arguments=($composeArguments + @('--project-name',$project,'ps','--all','--quiet','backend'))}).Trim()
if ($backend) {
    if ($backend -cnotmatch '^[a-f0-9]{12,64}$') { throw 'Expected at most one company backend container.' }
    $backendRecords = @(Invoke-BackupCommand @{File=$DockerExecutable; Arguments=@('inspect',$backend)} | ConvertFrom-Json -AsHashtable)
    $backendMetadata = $backendRecords[0]
    if ($backendMetadata.Config.Labels['com.docker.compose.project'] -cne $project -or $backendMetadata.Config.Labels['com.docker.compose.service'] -cne 'backend') { throw 'The backend container does not belong to the requested company.' }
    if ($backendMetadata.Config.Labels.Contains('org.opencontainers.image.revision')) { $recordedRevision = $backendMetadata.Config.Labels['org.opencontainers.image.revision'] }
}
$ReleaseCommit = Resolve-InVaultReleaseCommit -RecordedRevision $recordedRevision -ExplicitRevision $ReleaseCommit
$dump = 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --user="$MYSQL_USER" --single-transaction --quick --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --hex-blob --default-character-set=utf8mb4 --skip-add-drop-table "$MYSQL_DATABASE"'
$result = New-InVaultEncryptedBackup -CompanyId $CompanyId -ProjectName $project -DatabaseName $database `
    -ReleaseCommit $ReleaseCommit -DumpCommand @{File=$DockerExecutable; Arguments=@('exec', $container, 'sh', '-c', $dump)} `
    -OutputDirectory $OutputDirectory -RecipientFile $RecipientFile -AgeExecutable $AgeExecutable
if ($ReplicaDirectory) {
    # The operator supplies an already mounted off-host destination. No pruning occurs.
    $replicaRoot = (Resolve-Path -LiteralPath $ReplicaDirectory).Path
    $replica = Join-Path $replicaRoot ([IO.Path]::GetFileName($result.BackupFile))
    $partial = "$replica.part"
    try {
        [IO.File]::Copy($result.BackupFile, $partial, $false)
        if ((Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash -cne $result.Sha256) { throw 'Replica checksum mismatch.' }
        [IO.File]::Move($partial, $replica, $false)
    } finally { if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force } }
}
$result
