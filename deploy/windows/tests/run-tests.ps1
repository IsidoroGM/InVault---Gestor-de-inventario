#Requires -Version 7.3
param([ValidateSet('Unit','Render','Smoke')][string]$Mode = 'Unit', [string]$Installer)
$ErrorActionPreference = 'Stop'
$windows = Split-Path $PSScriptRoot -Parent
$repository = Split-Path (Split-Path $windows -Parent) -Parent
$output = Join-Path $repository 'releases/windows'
[void][IO.Directory]::CreateDirectory($output)
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$exe = Join-Path $output 'InstallerTests.exe'
$references = @('System.Windows.Forms','System.Drawing','System.ServiceProcess','System.Web.Extensions','System.Security','System.IO.Compression','System.IO.Compression.FileSystem') | ForEach-Object { "/reference:$_.dll" }
$sources = @('Core.cs','Runtime.cs','Setup.cs','RemoteAccess.cs') | ForEach-Object { Join-Path $windows $_ }
& $compiler /nologo /target:exe /platform:x64 /optimize+ /utf8output /main:InstallerTests "/out:$exe" @references @sources (Join-Path $PSScriptRoot 'InstallerTests.cs')
if ($LASTEXITCODE -ne 0) { throw 'Installer tests did not compile.' }
$run = Join-Path $output ("verification-$($Mode.ToLowerInvariant())-" + [guid]::NewGuid().ToString('N'))
if ($Mode -eq 'Smoke') {
    if (-not $Installer) { $Installer = Join-Path $output 'InVaultSetup.exe' }
    & $exe --smoke $run $Installer
} elseif ($Mode -eq 'Render') { & $exe --render $run }
else { & $exe --unit $run }
if ($LASTEXITCODE -ne 0) { throw "Installer $Mode verification failed. Evidence: $run" }
Write-Output "Evidence: $run"
