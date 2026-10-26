#Requires -Version 7.3
[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) 'releases/windows'),
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw 'Build this native x64 installer on Windows.' }
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$output = [IO.Path]::GetFullPath($OutputDirectory)
[void][IO.Directory]::CreateDirectory($output)
$cache = Join-Path $output 'cache'
[void][IO.Directory]::CreateDirectory($cache)
$work = Join-Path $output ('build-' + [guid]::NewGuid().ToString('N'))
$source = Join-Path $work 'source'
$payload = Join-Path $work 'payload'
$buildLog = Join-Path $output 'build-installer.log'
Set-Content -LiteralPath $buildLog -Value 'InVault Windows build' -Encoding utf8NoBOM
[void][IO.Directory]::CreateDirectory($source)
[void][IO.Directory]::CreateDirectory($payload)
$dependencies = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dependencies.json') -Raw | ConvertFrom-Json
function Invoke-Checked([string]$File, [string[]]$Arguments) {
    Write-Host "Running $([IO.Path]::GetFileName($File)) $($Arguments[0]) (log: $buildLog)"
    & $File @Arguments >> $buildLog 2>&1
    if ($LASTEXITCODE -ne 0) {
        Get-Content -LiteralPath $buildLog -Tail 35 | Write-Host
        throw "$([IO.Path]::GetFileName($File)) failed (exit $LASTEXITCODE). See $buildLog"
    }
}
function Copy-File([string]$From, [string]$To) {
    [void][IO.Directory]::CreateDirectory((Split-Path $To -Parent))
    Copy-Item -LiteralPath $From -Destination $To
}
function Read-PinnedDependency($Dependency) {
    $path = Join-Path $cache $Dependency.file
    if (-not (Test-Path -LiteralPath $path)) {
        if ($Offline) { throw "Missing offline dependency: $($Dependency.file)" }
        Invoke-WebRequest -Uri $Dependency.url -OutFile "$path.part"
        if ((Get-FileHash -LiteralPath "$path.part" -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Dependency.sha256) {
            Remove-Item -LiteralPath "$path.part"
            throw 'Downloaded dependency does not match the pinned SHA-256. Review the upstream release; never update the hash automatically.'
        }
        Move-Item -LiteralPath "$path.part" -Destination $path
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Dependency.sha256) { throw "Dependency integrity failure: $($Dependency.file)" }
    return $path
}
try {
    $baseRevision = (& git -C $repository rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $baseRevision -cnotmatch '^[a-f0-9]{40}$') { throw 'A full Git base revision is required.' }
    # Explicit allowlist: no .env, company data, development configuration or local artifacts.
    $files = & git -C $repository ls-files --cached --others --exclude-standard
    if ($LASTEXITCODE -ne 0) { throw 'Unable to enumerate source files.' }
    $files = @($files | Where-Object {
        ($_ -match '^(src/|frontend/(src/|public/|[^/]+\.(json|mjs))|deploy/windows/|\.mvn/wrapper/maven-wrapper.properties$|pom.xml$|mvnw.cmd$)') -and
        ($_ -notmatch '(^|/)(application-(dev|local)\.ya?ml|node_modules|target|dist|\.angular)(/|$)')
    } | Sort-Object -Unique)
    $sourceManifest = [ordered]@{}
    foreach ($file in $files) {
        $from = Join-Path $repository $file
        if (-not (Test-Path -LiteralPath $from -PathType Leaf)) { throw "Source file disappeared: $file" }
        $sourceManifest[$file] = (Get-FileHash -LiteralPath $from -Algorithm SHA256).Hash.ToLowerInvariant()
        Copy-File $from (Join-Path $source $file)
    }
    $sourceBytes = [Text.Encoding]::UTF8.GetBytes(($sourceManifest | ConvertTo-Json -Compress))
    $sourceDigest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($sourceBytes)).ToLowerInvariant()
    $buildWindows = Join-Path $source 'deploy/windows'
    if (-not $JavaHome) { $JavaHome = Split-Path (Split-Path (Get-Command java.exe).Source -Parent) -Parent }
    $javaVersion = (& (Join-Path $JavaHome 'bin/java.exe') --version | Select-Object -First 1)
    if ($javaVersion -notmatch '25\.') { throw 'The application requires JDK 25.' }
    $mysqlZip = Read-PinnedDependency $dependencies.mysql
    $caddyZip = Read-PinnedDependency $dependencies.caddy
    $vc = Read-PinnedDependency $dependencies.vcRuntime
    $tailscaleMsi = Read-PinnedDependency $dependencies.tailscale
    $tailscaleSignature = Get-AuthenticodeSignature -LiteralPath $tailscaleMsi
    if ($tailscaleSignature.Status -ne 'Valid' -or $tailscaleSignature.SignerCertificate.Subject -notmatch '(^|,\s*)CN=Tailscale Inc\.,') { throw 'The Tailscale MSI is not validly signed by Tailscale Inc.' }
    $signature = Get-AuthenticodeSignature -LiteralPath $vc
    if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'CN=Microsoft Corporation') { throw 'The Visual C++ prerequisite is not validly signed by Microsoft.' }
    $packageJson = Get-Content -LiteralPath (Join-Path $source 'frontend/package.json') -Raw | ConvertFrom-Json
    $version = $packageJson.version
    [xml]$pom = Get-Content -LiteralPath (Join-Path $source 'pom.xml') -Raw
    if ($pom.project.version -cne $version) { throw 'Backend and frontend versions differ.' }
    Push-Location $source
    try {
        $mavenArguments = @('--batch-mode', '--no-transfer-progress', 'clean', 'verify')
        if ($Offline) { $mavenArguments += '-o' }
        Invoke-Checked (Join-Path $source 'mvnw.cmd') $mavenArguments
    } finally { Pop-Location }
    Push-Location (Join-Path $source 'frontend')
    try {
        $npmArguments = @('ci', '--no-audit', '--no-fund')
        if ($Offline) { $npmArguments += '--offline' }
        Invoke-Checked 'npm.cmd' $npmArguments
        Invoke-Checked 'npm.cmd' @('run', 'test:ci')
        Invoke-Checked 'npm.cmd' @('run', 'build')
    } finally { Pop-Location }
    Copy-File (Join-Path $source "target/invault-backend-$version.jar") (Join-Path $payload 'backend.jar')
    $jar = [IO.Compression.ZipFile]::OpenRead((Join-Path $payload 'backend.jar'))
    try {
        if (@($jar.Entries | Where-Object { $_.FullName -match '^BOOT-INF/classes/application-(dev|local)\.ya?ml$' }).Count) { throw 'Local configuration entered the packaged JAR.' }
    } finally { $jar.Dispose() }
    Copy-Item -LiteralPath (Join-Path $source 'frontend/dist/frontend/browser') -Destination (Join-Path $payload 'frontend') -Recurse
    Write-Host 'Creating the private Java runtime.'
    Invoke-Checked (Join-Path $JavaHome 'bin/jlink.exe') @('--add-modules', 'java.se,jdk.crypto.ec,jdk.unsupported,jdk.management,jdk.naming.dns,jdk.charsets,jdk.localedata',
        '--include-locales=en,es', '--strip-debug', '--no-header-files', '--no-man-pages', '--output', (Join-Path $payload 'java'))
    [IO.Compression.ZipFile]::ExtractToDirectory($caddyZip, (Join-Path $payload 'caddy'))
    $archive = [IO.Compression.ZipFile]::OpenRead($mysqlZip)
    try {
        foreach ($entry in $archive.Entries) {
            $relative = $entry.FullName -replace '^[^/]+/', ''
            if ($entry.Length -eq 0) { continue }
            if ($relative -notmatch '^(bin/(mysqld|mysql|mysqldump|mysqladmin)\.exe$|bin/[^/]+\.dll$|lib/|share/|LICENSE$|README$|docs/INFO_)') { continue }
            if ($relative -match '\.(pdb|lib|a)$') { continue }
            $destination = Join-Path (Join-Path $payload 'mysql') $relative
            [void][IO.Directory]::CreateDirectory((Split-Path $destination -Parent))
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination)
        }
    } finally { $archive.Dispose() }
    Copy-File $vc (Join-Path $payload 'prerequisites/VC_redist.x64.exe')
    Copy-File $tailscaleMsi (Join-Path $payload 'prerequisites/tailscale.msi')
    Copy-File (Join-Path $buildWindows 'GUIA_INSTALACION_ES.md') (Join-Path $payload 'GUIA_INSTALACION_ES.md')
    Copy-File (Join-Path $buildWindows 'InVault.exe.config') (Join-Path $payload 'InVault.exe.config')
    Copy-File (Join-Path $buildWindows 'InVault.exe.config') (Join-Path $output 'InVaultSetup.exe.config')
    $compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
    $references = @('System.Windows.Forms','System.Drawing','System.ServiceProcess','System.Web.Extensions','System.Security','System.IO.Compression','System.IO.Compression.FileSystem') | ForEach-Object { "/reference:$_.dll" }
    $sources = @('Core.cs','Runtime.cs','Setup.cs','RemoteAccess.cs') | ForEach-Object { Join-Path $buildWindows $_ }
    $common = @('/nologo', '/target:winexe', '/platform:x64', '/optimize+', '/utf8output') + $references
    Invoke-Checked $compiler ($common + @("/out:$(Join-Path $payload 'InVault.exe')", "/win32manifest:$(Join-Path $buildWindows 'runtime.manifest')") + $sources)
    $hashes = [ordered]@{}
    Get-ChildItem -LiteralPath $payload -File -Recurse | Sort-Object FullName | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($payload, $_.FullName).Replace('\','/')
        $hashes[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $identityBytes = [Text.Encoding]::UTF8.GetBytes($sourceDigest + ($hashes | ConvertTo-Json -Compress))
    $packageId = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($identityBytes)).ToLowerInvariant().Substring(0,20)
    $manifest = [ordered]@{Format=1;Version=$version;PackageId=$packageId;BaseRevision=$baseRevision;SourceDigest=$sourceDigest;
        MySqlVersion=$dependencies.mysql.version;JavaVersion=$javaVersion;CaddyVersion=$dependencies.caddy.version;TailscaleVersion=$dependencies.tailscale.version;Files=$hashes}
    $manifestFile = Join-Path $work 'package.json'
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $manifestFile -Encoding utf8NoBOM
    $sourceManifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $output 'source-manifest.json') -Encoding utf8NoBOM
    $payloadZip = Join-Path $work 'payload.zip'
    [IO.Compression.ZipFile]::CreateFromDirectory($payload, $payloadZip, [IO.Compression.CompressionLevel]::Optimal, $false)
    $exe = Join-Path $output 'InVaultSetup.exe'
    Invoke-Checked $compiler ($common + @("/out:$exe", "/win32manifest:$(Join-Path $buildWindows 'installer.manifest')", "/resource:$payloadZip,payload.zip", "/resource:$manifestFile,package.json") + $sources)
    Copy-Item -LiteralPath $manifestFile -Destination (Join-Path $output 'package.json') -Force
    $checksum = (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant()
    "$checksum  InVaultSetup.exe" | Set-Content -LiteralPath (Join-Path $output 'InVaultSetup.exe.sha256') -Encoding ascii
    [pscustomobject]@{Installer=$exe;Version=$version;PackageId=$packageId;BaseRevision=$baseRevision;SourceDigest=$sourceDigest;Sha256=$checksum;SizeMB=[math]::Round((Get-Item -LiteralPath $exe).Length/1MB,1)}
} finally {
    $absoluteWork = [IO.Path]::GetFullPath($work)
    if ((Split-Path $absoluteWork -Parent) -cne $output -or (Split-Path $absoluteWork -Leaf) -notmatch '^build-[a-f0-9]{32}$') { throw 'Unsafe build cleanup path.' }
    if (Test-Path -LiteralPath $absoluteWork) { Remove-Item -LiteralPath $absoluteWork -Recurse -Force }
}
