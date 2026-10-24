#requires -Version 7.3
[CmdletBinding()]
param([Parameter(Mandatory)][string]$Destination)
$ErrorActionPreference = 'Stop'
$version = '1.3.2'
if ([Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne [Runtime.InteropServices.Architecture]::X64) { throw 'Install the official age package for your architecture manually.' }
if ($IsWindows) {
    $asset = "age-v$version-windows-amd64.zip"
    $sha256 = 'f48d8f8f9ebe903ab5027ed067652f2cc1db94bc206976430133b905dcd8e8c7'
} elseif ($IsLinux) {
    $asset = "age-v$version-linux-amd64.tar.gz"
    $sha256 = 'cbe24006683f8eb669266162894b9a522a1af52f2665fbc63a4bb032ed26ac10'
} else { throw 'Install age manually on this platform.' }
$root = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $root) { throw 'Destination must be a new directory.' }
[void][IO.Directory]::CreateDirectory($root)
$archive = Join-Path $root $asset
try {
    Invoke-WebRequest -Uri "https://github.com/FiloSottile/age/releases/download/v$version/$asset" -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $sha256) { throw 'Official age archive SHA-256 mismatch.' }
    if ($IsWindows) { Expand-Archive -LiteralPath $archive -DestinationPath $root } else {
        & tar -xzf $archive -C $root
        if ($LASTEXITCODE -ne 0) { throw 'Cannot extract age archive.' }
    }
} finally { if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive } }
Join-Path $root 'age'
