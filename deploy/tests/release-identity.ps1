#requires -Version 7.3
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot '../ReleaseIdentity.psm1') -Force
$cases=0
function Assert-True([bool]$Condition,[string]$Message) { if (-not $Condition) {throw $Message};$script:cases++ }
function Assert-Rejected([scriptblock]$Action) { $failed=$false;try { & $Action | Out-Null } catch {$failed=$true};Assert-True $failed 'An ambiguous or conflicting release was accepted.' }
$first='a'*40
$second='b'*40
Assert-True ((Resolve-InVaultReleaseCommit -RecordedRevision $first) -ceq $first) 'The running image revision was not selected.'
Assert-True ((Resolve-InVaultReleaseCommit -RecordedRevision $first -ExplicitRevision $first) -ceq $first) 'A matching explicit identity was rejected.'
Assert-True ((Resolve-InVaultReleaseCommit -RecordedRevision unversioned -ExplicitRevision $first) -ceq $first) 'A verified legacy revision could not be supplied.'
Assert-True ((Resolve-InVaultReleaseCommit -ExplicitRevision $first) -ceq $first) 'An explicit stopped/legacy deployment revision was rejected.'
Assert-Rejected { Resolve-InVaultReleaseCommit -RecordedRevision $first -ExplicitRevision $second }
Assert-Rejected { Resolve-InVaultReleaseCommit -RecordedRevision unversioned }
Assert-Rejected { Resolve-InVaultReleaseCommit }
Assert-Rejected { Resolve-InVaultReleaseCommit -ExplicitRevision abc123 }
Assert-Rejected { Resolve-InVaultReleaseCommit -RecordedRevision $first -ExplicitRevision ('ABCDEF'*7) }
Write-Host "Release identity passed: $cases checks for deployed metadata, legacy identity and mismatch rejection."
