#requires -Version 7.3
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Resolve-InVaultReleaseCommit {
    [CmdletBinding()]
    param([string]$RecordedRevision, [string]$ExplicitRevision)
    $known = $RecordedRevision -cmatch '^[a-f0-9]{40}$'
    if (-not [string]::IsNullOrEmpty($ExplicitRevision)) {
        if ($ExplicitRevision -cnotmatch '^[a-f0-9]{40}$') { throw 'ReleaseCommit must be a full lowercase Git commit SHA.' }
        if ($known -and $ExplicitRevision -cne $RecordedRevision) { throw 'ReleaseCommit does not match the deployed backend image.' }
        return $ExplicitRevision
    }
    if ($known) { return $RecordedRevision }
    throw 'The deployed backend revision is unknown. Supply the verified deployed ReleaseCommit for a legacy image; the checkout is not used as a fallback.'
}
Export-ModuleMember -Function Resolve-InVaultReleaseCommit
