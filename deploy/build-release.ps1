#requires -Version 7.3
[CmdletBinding()]
param([string]$OutputDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'releases'))
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'Backup.psm1') -Force -WarningAction SilentlyContinue
$repository=Split-Path $PSScriptRoot -Parent
$revision=(Invoke-BackupCommand @{File='git';Arguments=@('-C',$repository,'rev-parse','HEAD')}).Trim()
if ($revision -cnotmatch '^[a-f0-9]{40}$') { throw 'The checkout must have a full Git commit identity.' }
$changes=Invoke-BackupCommand @{File='git';Arguments=@('-C',$repository,'status','--porcelain','--untracked-files=normal')}
if (-not [string]::IsNullOrWhiteSpace($changes)) { throw 'Commit source changes before building an identified release.' }
$images=@{}
$temporaryTags=[Collections.Generic.List[string]]::new()
function Publish-ReleaseFile([string]$Path, [string]$Contents) {
    if (Test-Path -LiteralPath $Path) {
        if ([IO.File]::ReadAllText($Path) -cne $Contents) { throw 'An existing release artifact differs; it will not be overwritten.' }
        return
    }
    $partial="$Path.$([guid]::NewGuid().ToString('N')).part"
    try { [IO.File]::WriteAllText($partial,$Contents,[Text.UTF8Encoding]::new($false)); [IO.File]::Move($partial,$Path,$false) }
    finally { if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force } }
}
try {
    foreach ($service in @('backend','frontend')) {
        $tag="invault-$service`:$revision"
        $previous=$null
        try { $previous=(Invoke-BackupCommand @{File='docker';Arguments=@('image','inspect','--format','{{.Id}}',$tag)}).Trim() } catch { }
        $temporary="invault-$service`:build-$([guid]::NewGuid().ToString('N'))"
        $temporaryTags.Add($temporary)
        $context=if ($service -eq 'backend') {$repository} else {Join-Path $repository 'frontend'}
        Invoke-BackupCommand @{File='docker';Arguments=@('build','--build-arg',"INVAULT_BUILD_REVISION=$revision",'--tag',$temporary,$context)} | Out-Null
        $imageId=(Invoke-BackupCommand @{File='docker';Arguments=@('image','inspect','--format','{{.Id}}',$temporary)}).Trim()
        $label=(Invoke-BackupCommand @{File='docker';Arguments=@('image','inspect','--format','{{index .Config.Labels "org.opencontainers.image.revision"}}',$temporary)}).Trim()
        if ($imageId -cnotmatch '^sha256:[a-f0-9]{64}$' -or $label -cne $revision) { throw 'The built image did not preserve its release identity.' }
        if ($previous -and $previous -cne $imageId) { throw 'The release tag already identifies a different image; it will not be retagged.' }
        Invoke-BackupCommand @{File='docker';Arguments=@('image','tag',$temporary,$tag)} | Out-Null
        $images[$service]=[ordered]@{image=$tag;imageId=$imageId}
    }
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetFullPath($OutputDirectory))
    $root=(Resolve-Path -LiteralPath $OutputDirectory).Path
    $descriptor=Join-Path $root "invault-$revision.release.json"
    $overlay=Join-Path $root "invault-$revision.compose.json"
    $metadata=[ordered]@{formatVersion=1;revision=$revision;backend=$images.backend;frontend=$images.frontend}
    $compose=[ordered]@{services=[ordered]@{backend=[ordered]@{image=$images.backend.image;pull_policy='never'};frontend=[ordered]@{image=$images.frontend.image;pull_policy='never'}}}
    Publish-ReleaseFile $descriptor ($metadata | ConvertTo-Json -Depth 6)
    Publish-ReleaseFile $overlay ($compose | ConvertTo-Json -Depth 6)
    [PSCustomObject]@{Revision=$revision;BackendImage=$images.backend.image;FrontendImage=$images.frontend.image;Descriptor=$descriptor;ComposeFile=$overlay}
} finally {
    foreach ($temporary in $temporaryTags) {
        # Only this invocation's generated temporary tags; no pruning, force or volume operations.
        try { Invoke-BackupCommand @{File='docker';Arguments=@('image','rm',$temporary)} | Out-Null } catch { }
    }
}
