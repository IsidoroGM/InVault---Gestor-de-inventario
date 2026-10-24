#requires -Version 7.3
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Start-BackupProcess {
    param([hashtable]$Command, [switch]$InputStream, [switch]$OutputStream)
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Command.File
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $InputStream.IsPresent
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in $Command.Arguments) { $info.ArgumentList.Add([string]$argument) }
    if ($Command.ContainsKey('Environment')) {
        foreach ($key in $Command.Environment.Keys) { $info.Environment[$key] = $Command.Environment[$key] }
    }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    [void]$process.Start()
    # Drain stderr concurrently to prevent a full pipe from blocking a dump/import.
    $errorTask = $process.StandardError.ReadToEndAsync()
    $outputTask = if (-not $OutputStream) { $process.StandardOutput.ReadToEndAsync() } else { $null }
    return @{ Process = $process; ErrorTask = $errorTask; OutputTask = $outputTask }
}

function Complete-BackupProcess {
    param($Running, [string]$Operation)
    $Running.Process.WaitForExit()
    $errorText = $Running.ErrorTask.GetAwaiter().GetResult()
    $output = if ($Running.OutputTask) { $Running.OutputTask.GetAwaiter().GetResult() } else { '' }
    if ($Running.Process.ExitCode -ne 0) {
        # Native stderr may contain SQL or environment values. Do not put it in scheduled logs.
        throw "$Operation failed (exit $($Running.Process.ExitCode)); no backup/recovery is published."
    }
    return $output
}

function Stop-BackupProcess {
    param($Running)
    if ($Running) {
        if (-not $Running.Process.HasExited) { $Running.Process.Kill($true); $Running.Process.WaitForExit() }
        $Running.Process.Dispose()
    }
}

function Invoke-BackupCommand {
    param([hashtable]$Command)
    $running = Start-BackupProcess $Command
    try { return Complete-BackupProcess $running 'Command' } finally { Stop-BackupProcess $running }
}

function Assert-BackupCompany {
    param([string]$CompanyId)
    if ($CompanyId -cnotmatch '^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$' -or $CompanyId.Length -gt 24 -or
        $CompanyId.Length -lt 2 -or $CompanyId -eq 'preview') { throw 'Invalid company ID.' }
}

function New-PrivateBackupDirectory {
    $directory = Join-Path ([IO.Path]::GetTempPath()) ('invault-recovery-' + [guid]::NewGuid().ToString('N'))
    if ($IsWindows) {
        [void][IO.Directory]::CreateDirectory($directory)
        $acl = [Security.AccessControl.DirectorySecurity]::new()
        $acl.SetAccessRuleProtection($true, $false)
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
        $rule = [Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl',
            'ContainerInherit, ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
        Set-Acl -LiteralPath $directory -AclObject $acl
    } else {
        [void][IO.Directory]::CreateDirectory($directory,
            [IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite -bor [IO.UnixFileMode]::UserExecute)
    }
    return $directory
}

function New-InVaultEncryptedBackup {
    [CmdletBinding()]
    param([string]$CompanyId, [string]$ProjectName, [string]$DatabaseName,
        [string]$ReleaseCommit, [hashtable]$DumpCommand, [string]$OutputDirectory,
        [string]$RecipientFile, [string]$AgeExecutable = 'age')
    Assert-BackupCompany $CompanyId
    if ($ProjectName -cne "invault-$CompanyId" -or
        $DatabaseName -cne ('invault_' + $CompanyId.Replace('-', '_'))) { throw 'Company/project/database mismatch.' }
    if ($ReleaseCommit -cnotmatch '^[a-f0-9]{40}$') { throw 'ReleaseCommit must be the full deployed Git commit SHA.' }
    $recipient = (Resolve-Path -LiteralPath $RecipientFile).Path
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetFullPath($OutputDirectory))
    $created = [DateTime]::UtcNow
    $filename = "$CompanyId-$($created.ToString('yyyyMMddTHHmmssfffZ'))-$([guid]::NewGuid().ToString('N')).zip.age"
    $destination = Join-Path ([IO.Path]::GetFullPath($OutputDirectory)) $filename
    $partial = "$destination.part"
    $encrypt = $null
    $dump = $null
    $zip = $null
    try {
        $encrypt = Start-BackupProcess @{File=$AgeExecutable; Arguments=@('--encrypt', '--recipients-file', $recipient, '--output', $partial)} -InputStream
        $zip = [IO.Compression.ZipArchive]::new($encrypt.Process.StandardInput.BaseStream, [IO.Compression.ZipArchiveMode]::Create, $true)
        $manifest = [ordered]@{formatVersion=1; companyId=$CompanyId; projectName=$ProjectName;
            databaseName=$DatabaseName; createdUtc=$created.ToString('O'); releaseCommit=$ReleaseCommit;
            dumpTool='mysqldump'; consistency='single-transaction; InnoDB; no concurrent schema changes'}
        $manifestStream = $zip.CreateEntry('manifest.json').Open()
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes(($manifest | ConvertTo-Json -Compress))
            $manifestStream.Write($bytes)
        } finally { $manifestStream.Dispose() }
        $sql = $zip.CreateEntry('database.sql', [IO.Compression.CompressionLevel]::Optimal).Open()
        try {
            $dump = Start-BackupProcess $DumpCommand -OutputStream
            $dump.Process.StandardOutput.BaseStream.CopyTo($sql)
            Complete-BackupProcess $dump 'Database dump' | Out-Null
        } finally { $sql.Dispose() }
        $zip.Dispose(); $zip = $null
        $encrypt.Process.StandardInput.Close()
        Complete-BackupProcess $encrypt 'Backup encryption' | Out-Null
        [IO.File]::Move($partial, $destination, $false)
        return [pscustomobject]@{CompanyId=$CompanyId; BackupFile=$destination;
            Sha256=(Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash;
            Bytes=([IO.FileInfo]::new($destination)).Length; CreatedUtc=$manifest.createdUtc; ReleaseCommit=$ReleaseCommit}
    } finally {
        try { if ($zip) { $zip.Dispose() } } finally {
            Stop-BackupProcess $dump
            Stop-BackupProcess $encrypt
            if (Test-Path -LiteralPath $partial) { Remove-Item -LiteralPath $partial -Force }
        }
    }
}

function Invoke-InVaultBackupRestore {
    [CmdletBinding()]
    param([string]$BackupFile, [string]$CompanyId, [string]$IdentityFile,
        [scriptblock]$RestoreAction, [string]$AgeExecutable = 'age')
    Assert-BackupCompany $CompanyId
    $backup = (Resolve-Path -LiteralPath $BackupFile).Path
    $identity = (Resolve-Path -LiteralPath $IdentityFile).Path
    $privateDirectory = New-PrivateBackupDirectory
    $decryptedFile = Join-Path $privateDirectory 'verified.zip'
    $decrypt = $null
    $zip = $null
    try {
        $decrypt = Start-BackupProcess @{File=$AgeExecutable; Arguments=@('--decrypt', '--identity', $identity, $backup)} -OutputStream
        $file = [IO.File]::Open($decryptedFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $decrypt.Process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
        # Authenticate the complete age stream before opening a database or importing any SQL.
        Complete-BackupProcess $decrypt 'Backup decryption/authentication' | Out-Null
        $zip = [IO.Compression.ZipFile]::OpenRead($decryptedFile)
        if ($zip.Entries.Count -ne 2 -or -not $zip.GetEntry('manifest.json') -or -not $zip.GetEntry('database.sql') -or
            $zip.GetEntry('manifest.json').Length -gt 65536 -or $zip.GetEntry('database.sql').Length -eq 0) { throw 'Invalid backup archive.' }
        $reader = [IO.StreamReader]::new($zip.GetEntry('manifest.json').Open(), [Text.Encoding]::UTF8)
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($manifest.formatVersion -ne 1 -or $manifest.companyId -cne $CompanyId -or
            $manifest.projectName -cne "invault-$CompanyId" -or
            $manifest.databaseName -cne ('invault_' + $CompanyId.Replace('-', '_')) -or
            $manifest.releaseCommit -cnotmatch '^[a-f0-9]{40}$') { throw 'Backup does not belong to the requested company or format.' }
        $sql = $zip.GetEntry('database.sql').Open()
        try { & $RestoreAction $manifest $sql } finally { $sql.Dispose() }
    } finally {
        if ($zip) { $zip.Dispose() }
        Stop-BackupProcess $decrypt
        # Remove only the directory generated by this function, after checking its resolved parent and name.
        $resolved = [IO.Path]::GetFullPath($privateDirectory)
        if ((Split-Path $resolved -Parent) -cne ([IO.Path]::GetTempPath().TrimEnd([IO.Path]::DirectorySeparatorChar)) -or
            (Split-Path $resolved -Leaf) -notmatch '^invault-recovery-[a-f0-9]{32}$') { throw 'Unexpected recovery temporary path.' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

function Write-BackupSql {
    param([IO.Stream]$Sql, [hashtable]$Command)
    $running = Start-BackupProcess $Command -InputStream
    try {
        $Sql.CopyTo($running.Process.StandardInput.BaseStream)
        $running.Process.StandardInput.Close()
        Complete-BackupProcess $running 'Database restore' | Out-Null
    } finally { Stop-BackupProcess $running }
}

Export-ModuleMember -Function Assert-BackupCompany, Invoke-BackupCommand, New-InVaultEncryptedBackup, Invoke-InVaultBackupRestore, Write-BackupSql
