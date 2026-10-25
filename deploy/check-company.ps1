#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$CompanyId,
    [Parameter(Mandatory)][string]$EnvironmentFile,
    [switch]$ConfigurationOnly,
    [ValidatePattern('^[a-f0-9]{40}$')][string]$ExpectedRevision,
    [string]$BackupDirectory,
    [string]$ReplicaDirectory,
    [ValidateRange(1,8760)][double]$MaxBackupAgeHours = 30
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Backup.psm1') -Force -WarningAction SilentlyContinue
Import-Module (Join-Path $PSScriptRoot 'CompanyStatus.psm1') -Force -WarningAction SilentlyContinue
Import-Module (Join-Path $PSScriptRoot 'BackupStatus.psm1') -Force -WarningAction SilentlyContinue
Assert-BackupCompany $CompanyId
if ($ReplicaDirectory -and -not $BackupDirectory) { throw 'ReplicaDirectory requires BackupDirectory.' }
if ($ConfigurationOnly -and $BackupDirectory) { throw 'ConfigurationOnly cannot evaluate backup state.' }
$environmentPath = (Resolve-Path -LiteralPath $EnvironmentFile).Path
$repository = Split-Path $PSScriptRoot -Parent
$compose = @('compose','--env-file',$environmentPath,'--file',(Join-Path $repository 'compose.prod.yml'))
try {
    $raw = Invoke-BackupCommand @{File='docker'; Arguments=($compose + @('config','--format','json'))}
    try { $model = $raw | ConvertFrom-Json -AsHashtable } catch { throw 'Compose configuration could not be read.' }
    $configuration = Get-InVaultCompanyConfigurationStatus -CompanyId $CompanyId -Model $model
    $report = [ordered]@{companyId=$CompanyId; checkedUtc=[DateTimeOffset]::UtcNow.ToString('O'); healthy=$configuration.configurationValid; configuration=$configuration; runtimeChecked=$false; readinessChecked=$false; publicHttpsChecked=$false; backupsChecked=$false}
    if ($configuration.configurationValid -and -not $ConfigurationOnly) {
        $containers = [Collections.Generic.List[object]]::new()
        foreach ($service in @('database','backend','frontend','gateway')) {
            $ids = (Invoke-BackupCommand @{File='docker'; Arguments=($compose + @('--project-name',$configuration.project,'ps','--all','--quiet',$service))}).Trim()
            foreach ($id in @($ids -split '\r?\n' | Where-Object {$_ -cmatch '^[a-f0-9]{12,64}$'})) {
                $inspection = Invoke-BackupCommand @{File='docker'; Arguments=@('inspect',$id)}
                try { $decoded = $inspection | ConvertFrom-Json -AsHashtable } catch { throw 'Container metadata could not be read.' }
                foreach ($container in $decoded) { $containers.Add($container) }
            }
        }
        $runtime = Get-InVaultCompanyRuntimeStatus -CompanyId $CompanyId -Model $model -Containers $containers.ToArray() -ExpectedRevision $ExpectedRevision
        $report.runtimeChecked = $true; $report.runtime = $runtime
        $report.healthy = $report.healthy -and $runtime.healthy
        if ($runtime.healthy) {
            $report.readinessChecked = $true
            $available = $false
            try {
                $backend = @($containers | Where-Object { $_['Config']['Labels']['com.docker.compose.service'] -ceq 'backend' })[0]
                $probe = Invoke-BackupCommand @{File='docker'; Arguments=@('exec',$backend['Id'],'curl','--fail','--silent','--max-time','8','--connect-timeout','2','http://localhost:8080/api/health/readiness')}
                $available = ($probe | ConvertFrom-Json).status -ceq 'UP'
            } catch { }
            $report.readinessAvailable = $available
            $report.healthy = $report.healthy -and $available
        }
    }
    if ($BackupDirectory -and $configuration.configurationValid) {
        $arguments = @{CompanyId=$CompanyId; BackupDirectory=$BackupDirectory; MaxAgeHours=$MaxBackupAgeHours}
        if ($PSBoundParameters.ContainsKey('ReplicaDirectory')) { $arguments.ReplicaDirectory = $ReplicaDirectory }
        $backup = Get-InVaultBackupStatus @arguments
        $report.backupsChecked = $true; $report.backups = $backup
        $report.healthy = $report.healthy -and $backup.healthy
    }
    $report | ConvertTo-Json -Depth 8
    if (-not $report.healthy) { exit 2 }
} catch {
    # Compose/inspect metadata contains credentials; never echo the native payload or exception.
    [PSCustomObject]@{companyId=$CompanyId; healthy=$false; error='Company diagnostics could not complete.'} | ConvertTo-Json
    exit 1
}
