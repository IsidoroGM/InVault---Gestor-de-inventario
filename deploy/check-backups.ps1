#requires -Version 7.3
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$CompanyId,
    [Parameter(Mandatory)][string]$BackupDirectory,
    [ValidateRange(1, 8760)][double]$MaxAgeHours = 30,
    [string]$ReplicaDirectory
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'BackupStatus.psm1') -Force
$result = Get-InVaultBackupStatus @PSBoundParameters
$result | ConvertTo-Json -Depth 5
if (-not $result.healthy) { exit 2 }
