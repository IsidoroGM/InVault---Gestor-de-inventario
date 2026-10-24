#requires -Version 7.3
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-InVaultBackupStatus {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$CompanyId,
        [Parameter(Mandatory)][string]$BackupDirectory,
        [ValidateRange(1, 8760)][double]$MaxAgeHours = 30,
        [string]$ReplicaDirectory,
        [DateTimeOffset]$Now = [DateTimeOffset]::UtcNow
    )
    if ($CompanyId -cnotmatch '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$' -or $CompanyId.Length -lt 2 -or $CompanyId.Length -gt 24 -or $CompanyId -eq 'preview') {
        throw 'Invalid company ID.'
    }
    if ($PSBoundParameters.ContainsKey('ReplicaDirectory') -and [string]::IsNullOrWhiteSpace($ReplicaDirectory)) {
        throw 'Replica directory must not be empty when specified.'
    }
    $checks = [Collections.Generic.List[object]]::new()
    $report = [ordered]@{
        companyId=$CompanyId; checkedUtc=$Now.UtcDateTime.ToString('O'); healthy=$false
        maxAgeHours=$MaxAgeHours; backupFile=$null; createdUtc=$null; ageHours=$null
        replicaChecked=(-not [string]::IsNullOrWhiteSpace($ReplicaDirectory)); sha256=$null; checks=$checks
    }
    $root = [IO.Path]::GetFullPath($BackupDirectory)
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        $checks.Add(@{code='backup_directory_missing'; ok=$false})
        return [PSCustomObject]$report
    }
    $pattern = '^' + [regex]::Escape($CompanyId) + '-(?<date>\d{8}T\d{9}Z)-[a-f0-9]{32}\.zip\.age$'
    $candidates = @(
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Filter '*.zip.age') {
            if ($file.Name -cmatch $pattern) {
                $timestamp = [DateTimeOffset]::MinValue
                $style = [Globalization.DateTimeStyles]::AssumeUniversal -bor [Globalization.DateTimeStyles]::AdjustToUniversal
                if ([DateTimeOffset]::TryParseExact($Matches.date, "yyyyMMdd'T'HHmmssfff'Z'",
                        [Globalization.CultureInfo]::InvariantCulture, $style, [ref]$timestamp)) {
                    [PSCustomObject]@{File=$file; Created=$timestamp}
                }
            }
        }
    )
    if ($candidates.Count -eq 0) {
        $checks.Add(@{code='company_backup_missing'; ok=$false})
        return [PSCustomObject]$report
    }
    $latest = $candidates | Sort-Object Created -Descending | Select-Object -First 1
    $file = $latest.File
    $age = ($Now - $latest.Created).TotalHours
    $report.backupFile = $file.FullName
    $report.createdUtc = $latest.Created.UtcDateTime.ToString('O')
    $report.ageHours = [Math]::Round($age, 3)
    $checks.Add(@{code='backup_not_in_future'; ok=($age -ge (-5.0 / 60))})
    $checks.Add(@{code='backup_recent'; ok=($age -le $MaxAgeHours)})
    $checks.Add(@{code='backup_not_empty'; ok=($file.Length -gt 100)})

    # Only a structural probe: authenticated decryption and SQL verification belong to recovery.
    $stream = $null
    try {
        $stream = [IO.File]::OpenRead($file.FullName)
        $header = [byte[]]::new(22)
        $read = $stream.Read($header, 0, $header.Length)
        $checks.Add(@{code='age_header'; ok=($read -eq 22 -and [Text.Encoding]::ASCII.GetString($header) -ceq "age-encryption.org/v1`n")})
    } catch {
        $checks.Add(@{code='backup_readable'; ok=$false})
    } finally {
        if ($stream) { $stream.Dispose() }
    }

    if ($report.replicaChecked) {
        $replicaRoot = [IO.Path]::GetFullPath($ReplicaDirectory)
        $comparison = if ($IsWindows) { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
        $distinct = -not $root.TrimEnd([IO.Path]::DirectorySeparatorChar).Equals(
            $replicaRoot.TrimEnd([IO.Path]::DirectorySeparatorChar), $comparison)
        $checks.Add(@{code='replica_distinct_directory'; ok=$distinct})
        $replica = Join-Path $replicaRoot $file.Name
        $exists = Test-Path -LiteralPath $replica -PathType Leaf
        $checks.Add(@{code='replica_present'; ok=$exists})
        if ($exists -and $distinct) {
            try {
                $sameSize = (Get-Item -LiteralPath $replica).Length -eq $file.Length
                $checks.Add(@{code='replica_size'; ok=$sameSize})
                if ($sameSize) {
                    $localHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
                    $remoteHash = (Get-FileHash -LiteralPath $replica -Algorithm SHA256).Hash
                    $report.sha256 = $localHash
                    $checks.Add(@{code='replica_sha256'; ok=($localHash -ceq $remoteHash)})
                }
            } catch { $checks.Add(@{code='replica_readable'; ok=$false}) }
        }
    }
    $report.healthy = @($checks | Where-Object { -not $_.ok }).Count -eq 0
    return [PSCustomObject]$report
}

Export-ModuleMember -Function Get-InVaultBackupStatus
