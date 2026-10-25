#requires -Version 7.3
[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$repository=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repository 'deploy/Backup.psm1') -Force -WarningAction SilentlyContinue
$probe=Join-Path $repository 'src/main/resources/application-local.yml'
$marker='invault-release-ignored-config-probe'
$ownedProbe=$false
$container=$null
$jar=Join-Path ([IO.Path]::GetTempPath()) ('invault-release-jar-' + [guid]::NewGuid().ToString('N') + '.jar')
try {
    # CreateNew refuses to touch a developer's existing local configuration.
    $stream=[IO.File]::Open($probe,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $ownedProbe=$true
    try { $bytes=[Text.Encoding]::UTF8.GetBytes($marker);$stream.Write($bytes) } finally {$stream.Dispose()}
    $release=& (Join-Path $repository 'deploy/build-release.ps1') -OutputDirectory $OutputDirectory
    $container=(Invoke-BackupCommand @{File='docker';Arguments=@('create','--network','none',$release.BackendImage)}).Trim()
    if ($container -cnotmatch '^[a-f0-9]{64}$') {throw 'Unexpected test container identity.'}
    Invoke-BackupCommand @{File='docker';Arguments=@('cp',"${container}:/opt/invault/app.jar",$jar)} | Out-Null
    $zip=[IO.Compression.ZipFile]::OpenRead($jar)
    try {
        if ($zip.GetEntry('BOOT-INF/classes/application-local.yml') -or $zip.GetEntry('BOOT-INF/classes/application-dev.yml')) {throw 'An ignored local configuration entered the release JAR.'}
        if (-not $zip.GetEntry('BOOT-INF/classes/application-prod.yml')) {throw 'The production configuration was missing from the release.'}
    } finally {$zip.Dispose()}
    Write-Host 'Identified release built and verified: tracked source only; local/dev configuration excluded from the actual application JAR.'
    $release
} finally {
    if ($container -and $container -cmatch '^[a-f0-9]{64}$') {Invoke-BackupCommand @{File='docker';Arguments=@('rm','--volumes',$container)} | Out-Null}
    if (Test-Path -LiteralPath $jar) {Remove-Item -LiteralPath $jar -Force}
    if ($ownedProbe -and (Test-Path -LiteralPath $probe)) {
        if ([IO.File]::ReadAllText($probe) -cne $marker) {throw 'The local test probe changed; it is being preserved.'}
        Remove-Item -LiteralPath $probe -Force
    }
}
