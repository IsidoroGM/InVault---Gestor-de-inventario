#Requires -Version 7.3
[CmdletBinding()]
param([string]$BackendJar, [string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$windows = Split-Path $PSScriptRoot -Parent
$repository = Split-Path (Split-Path $windows -Parent) -Parent
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $repository 'releases/windows/recovery' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $BackendJar) {
    $versionRoot = Join-Path ([Environment]::GetFolderPath('ProgramFiles')) 'InVault/versions'
    $candidates = @(Get-ChildItem -LiteralPath $versionRoot -Filter backend.jar -File -Recurse -ErrorAction SilentlyContinue)
    if ($candidates.Count -ne 1) { throw 'Specify the installed backend JAR explicitly; no installation will be guessed.' }
    $BackendJar = $candidates[0].FullName
}
$work = Join-Path $output ('build-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($work)
try {
    $jar = [IO.Compression.ZipFile]::OpenRead($BackendJar)
    try {
        $crypto = @($jar.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/spring-security-crypto-[^/]+\.jar$' })
        if ($crypto.Count -ne 1) { throw 'The installed Spring crypto library is ambiguous or missing.' }
        $cryptoFile = Join-Path $work 'spring-security-crypto.jar'
        [IO.Compression.ZipFileExtensions]::ExtractToFile($crypto[0], $cryptoFile)
    } finally { $jar.Dispose() }
    & javac.exe --release 25 -encoding UTF-8 -cp $cryptoFile -d $work (Join-Path $PSScriptRoot 'InVaultAdminRecovery.java')
    if ($LASTEXITCODE -ne 0) { throw 'Recovery helper compilation failed.' }
    Copy-Item -LiteralPath (Join-Path $work 'InVaultAdminRecovery.class') -Destination (Join-Path $output 'InVaultAdminRecovery.class') -Force
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    $references = @('System.Windows.Forms','System.Drawing','System.ServiceProcess','System.Web.Extensions','System.Security','System.IO.Compression','System.IO.Compression.FileSystem') | ForEach-Object { "/reference:$_.dll" }
    $sources = @('Core.cs','Runtime.cs','Setup.cs','RemoteAccess.cs') | ForEach-Object { Join-Path $windows $_ }
    $exe = Join-Path $output 'InVaultRestablecerAcceso.exe'
    & $compiler /nologo /target:winexe /platform:x64 /optimize+ /utf8output /main:InVault.Windows.RecoveryProgram "/out:$exe" "/win32manifest:$(Join-Path $windows 'installer.manifest')" "/resource:$(Join-Path $work 'InVaultAdminRecovery.class'),InVaultAdminRecovery.class" @references @sources (Join-Path $PSScriptRoot 'AccountRecovery.cs')
    if ($LASTEXITCODE -ne 0) { throw 'Recovery GUI compilation failed.' }
    Copy-Item -LiteralPath (Join-Path $windows 'InVault.exe.config') -Destination ($exe + '.config') -Force
    $hash = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  InVaultRestablecerAcceso.exe" | Set-Content -LiteralPath ($exe + '.sha256') -Encoding ascii
    [pscustomobject]@{Executable=$exe;Sha256=$hash;BackendJar=$BackendJar}
} finally {
    $absoluteWork = [IO.Path]::GetFullPath($work)
    if ((Split-Path $absoluteWork -Parent) -cne $output -or (Split-Path $absoluteWork -Leaf) -notmatch '^build-[a-f0-9]{32}$') { throw 'Unsafe recovery build cleanup path.' }
    if (Test-Path -LiteralPath $absoluteWork) { Remove-Item -LiteralPath $absoluteWork -Recurse -Force }
}
