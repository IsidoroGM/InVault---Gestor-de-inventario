#requires -Version 7.3
[CmdletBinding()]
param([Parameter(Mandatory)][string]$AgeDirectory, [string]$MySqlHome)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Import-Module (Join-Path $repository 'deploy/Backup.psm1') -Force
$extension = if ($IsWindows) { '.exe' } else { '' }
$age = Join-Path $AgeDirectory "age$extension"
$keygen = Join-Path $AgeDirectory "age-keygen$extension"
$testId = [guid]::NewGuid().ToString('N').Substring(0, 10)
$company = "check-$testId"
$source = 'invault_' + $company.Replace('-', '_')
$target = "invault_recovery_$testId"
$testRoot = Join-Path ([IO.Path]::GetTempPath()) "invault-backup-test-$testId"
[void][IO.Directory]::CreateDirectory($testRoot)
$container = $null
$recoveryContainer = $null
$compose = $null
$clientArguments = @()

function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Assert-Rejected([scriptblock]$Action) {
    $failed = $false
    try { & $Action | Out-Null } catch { $failed = $true }
    Assert-True $failed 'Expected backup/restore operation to reject its input.'
}
function Client-Command([string]$Database, [string]$Sql) {
    if ($MySqlHome) {
        $args = $clientArguments + @('--batch', '--skip-column-names')
        if ($Database) { $args += @('--database', $Database) }
        if ($Sql) { $args += @('--execute', $Sql) }
        return @{File=(Join-Path $MySqlHome 'bin/mysql.exe'); Arguments=$args}
    }
    $args = @('exec', '--interactive', $container, 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root --batch --skip-column-names "$@"', 'mysql')
    if ($Database) { $args += @('--database', $Database) }
    if ($Sql) { $args += @('--execute', $Sql) }
    return @{File='docker'; Arguments=$args}
}
function Execute-Sql([string]$Database, [string]$Sql) {
    $stream = [IO.MemoryStream]::new([Text.Encoding]::UTF8.GetBytes($Sql))
    try { Write-BackupSql -Sql $stream -Command (Client-Command $Database '') } finally { $stream.Dispose() }
}

try {
    $identity = Join-Path $testRoot 'identity.agekey'
    $wrongIdentity = Join-Path $testRoot 'wrong.agekey'
    Invoke-BackupCommand @{File=$keygen; Arguments=@('--output', $identity)} | Out-Null
    Invoke-BackupCommand @{File=$keygen; Arguments=@('--output', $wrongIdentity)} | Out-Null
    $recipient = Join-Path $testRoot 'recipient.txt'
    Invoke-BackupCommand @{File=$keygen; Arguments=@('-y', $identity)} | Set-Content -LiteralPath $recipient
    if ($MySqlHome) {
        if (-not $env:INVAULT_TEST_MYSQL_PASSWORD) { throw 'Use the isolated test-mysql runner for native recovery testing.' }
        $config = Join-Path $testRoot 'client.cnf'
        Set-Content -LiteralPath $config -Value @"
[client]
user=root
password=$env:INVAULT_TEST_MYSQL_PASSWORD
host=127.0.0.1
port=$env:INVAULT_TEST_MYSQL_PORT
protocol=TCP
"@
        $clientArguments = @("--defaults-extra-file=$config")
        Execute-Sql '' "CREATE DATABASE $source; CREATE DATABASE $target;"
    } else {
        $config = & (Join-Path $repository 'deploy/new-company.ps1') -CompanyId $company -Domain "$company.example.test" -AdminEmail 'test@example.test' -OutputDirectory $testRoot
        $compose = @('compose', '--project-name', $config.ProjectName, '--env-file', $config.EnvironmentFile,
            '--file', (Join-Path $repository 'compose.prod.yml'))
        Invoke-BackupCommand @{File='docker'; Arguments=($compose + @('up', '--detach', '--wait', 'database'))} | Out-Null
        $container = (Invoke-BackupCommand @{File='docker'; Arguments=($compose + @('ps', '--quiet', 'database'))}).Trim()
        Execute-Sql '' "CREATE DATABASE $target;"
    }
    $schema = (Get-ChildItem (Join-Path $repository 'src/main/resources/db/migration') -Filter '*.sql' | Sort-Object Name |
        ForEach-Object { [IO.File]::ReadAllText($_.FullName) }) -join "`n"
    Execute-Sql $source $schema
    Execute-Sql $source @'
INSERT INTO roles VALUES (1,'ADMIN','Administrator',1,NOW(6),NULL);
INSERT INTO users (id,username,email,password_hash,active,must_change_password,created_at,token_version) VALUES (1,'restored-admin','restore@example.test','test-hash',1,0,NOW(6),7);
INSERT INTO user_roles VALUES (1,1);
INSERT INTO categories (id,name,active,created_at) VALUES (1,'Materia prima',1,NOW(6));
INSERT INTO locations (id,name,active,created_at) VALUES (1,'Almacén Ñ',1,NOW(6));
INSERT INTO units (id,code,name,active,created_at) VALUES (1,'KG','Kilogramo',1,NOW(6));
INSERT INTO products (id,sku,name,category_id,location_id,unit_id,minimum_stock,active,created_at) VALUES (1,'BACKUP-001','Aceite áéí',1,1,1,2.500,1,NOW(6));
INSERT INTO batches (id,product_id,batch_code,quantity,status,created_at) VALUES (1,1,'LOT-BACKUP',10.125,'AVAILABLE',NOW(6));
INSERT INTO stock_movements (id,product_id,batch_id,user_id,movement_type,quantity,previous_batch_quantity,new_batch_quantity,movement_date) VALUES (1,1,1,1,'INBOUND',10.125,0.000,10.125,NOW(6));
INSERT INTO audit_logs (id,user_id,action,entity_name,entity_id,created_at,before_data,after_data) VALUES (1,1,'STOCK_MOVEMENT_CREATED','StockMovement',1,NOW(6),'{}','{"quantity":10.125}');
'@
    $commit = 'a' * 40
    if ($MySqlHome) {
        $dump = @{File=(Join-Path $MySqlHome 'bin/mysqldump.exe'); Arguments=($clientArguments + @('--single-transaction', '--quick', '--no-tablespaces', '--set-gtid-purged=OFF', '--skip-add-drop-table', '--default-character-set=utf8mb4', $source))}
        $backup = New-InVaultEncryptedBackup -CompanyId $company -ProjectName "invault-$company" -DatabaseName $source `
            -ReleaseCommit $commit -DumpCommand $dump -OutputDirectory $testRoot -RecipientFile $recipient -AgeExecutable $age
    } else {
        $replica = Join-Path $testRoot 'replica'
        [void][IO.Directory]::CreateDirectory($replica)
        $backup = & (Join-Path $repository 'deploy/backup-company.ps1') -CompanyId $company -EnvironmentFile $config.EnvironmentFile `
            -RecipientFile $recipient -OutputDirectory $testRoot -ReplicaDirectory $replica -ReleaseCommit $commit -AgeExecutable $age
        $replicaFile = Join-Path $replica ([IO.Path]::GetFileName($backup.BackupFile))
        Assert-True ((Get-FileHash -LiteralPath $replicaFile -Algorithm SHA256).Hash -eq $backup.Sha256) 'Replica checksum mismatch.'
        Assert-Rejected { & (Join-Path $repository 'deploy/backup-company.ps1') -CompanyId 'other-company' -EnvironmentFile $config.EnvironmentFile -RecipientFile $recipient -OutputDirectory $testRoot -AgeExecutable $age }
    }
    Assert-True ($backup.Bytes -gt 100) 'Encrypted backup is unexpectedly empty.'
    Assert-True ([IO.File]::ReadAllText($backup.BackupFile).StartsWith('age-encryption.org/v1')) 'Backup is not an age file.'
    $probe = @{Touched=$false}
    $forbiddenImport = { param($manifest, $sql) $probe.Touched = $true; throw 'Import must not begin.' }
    Assert-Rejected { Invoke-InVaultBackupRestore -BackupFile $backup.BackupFile -CompanyId 'other-company' -IdentityFile $identity -AgeExecutable $age -RestoreAction $forbiddenImport }
    Assert-Rejected { Invoke-InVaultBackupRestore -BackupFile $backup.BackupFile -CompanyId $company -IdentityFile $wrongIdentity -AgeExecutable $age -RestoreAction $forbiddenImport }
    $tampered = Join-Path $testRoot 'tampered.age'
    $bytes = [IO.File]::ReadAllBytes($backup.BackupFile)
    $bytes[$bytes.Length - 20] = $bytes[$bytes.Length - 20] -bxor 1
    [IO.File]::WriteAllBytes($tampered, $bytes)
    Assert-Rejected { Invoke-InVaultBackupRestore -BackupFile $tampered -CompanyId $company -IdentityFile $identity -AgeExecutable $age -RestoreAction $forbiddenImport }
    [IO.File]::WriteAllBytes($tampered, $bytes[0..($bytes.Length - 100)])
    Assert-Rejected { Invoke-InVaultBackupRestore -BackupFile $tampered -CompanyId $company -IdentityFile $identity -AgeExecutable $age -RestoreAction $forbiddenImport }
    Assert-True (-not $probe.Touched) 'An invalid backup reached the SQL importer.'
    # A dump failure after writing bytes must not publish a partial encrypted backup.
    $before = @(Get-ChildItem -LiteralPath $testRoot -Filter '*.zip.age').Count
    $badDump = Client-Command $source 'SELECT * FROM nonexistent_backup_test_table'
    Assert-Rejected { New-InVaultEncryptedBackup -CompanyId $company -ProjectName "invault-$company" -DatabaseName $source `
        -ReleaseCommit $commit -DumpCommand $badDump -OutputDirectory $testRoot -RecipientFile $recipient -AgeExecutable $age }
    Assert-True (@(Get-ChildItem -LiteralPath $testRoot -Filter '*.zip.age').Count -eq $before) 'Failed dump published a backup.'
    Assert-True (@(Get-ChildItem -LiteralPath $testRoot -Filter '*.part').Count -eq 0) 'Partial backup was not removed.'
    # Remove source records after the backup, then recover from the encrypted artifact alone.
    Execute-Sql $source 'DELETE FROM audit_logs; DELETE FROM stock_movements; DELETE FROM batches;'
    if ($MySqlHome) {
        Invoke-InVaultBackupRestore -BackupFile $backup.BackupFile -CompanyId $company -IdentityFile $identity -AgeExecutable $age -RestoreAction {
            param($manifest, $sql)
            Write-BackupSql -Sql $sql -Command (Client-Command $target '')
        }
    } else {
        $restored = & (Join-Path $repository 'deploy/restore-company.ps1') -CompanyId $company -BackupFile $backup.BackupFile -IdentityFile $identity -AgeExecutable $age
        $recoveryContainer = $restored.ContainerId
        $container = $recoveryContainer
        $target = $source
        $inspect = (Invoke-BackupCommand @{File='docker'; Arguments=@('inspect', $container)} | ConvertFrom-Json)[0]
        Assert-True ($inspect.HostConfig.NetworkMode -eq 'none') 'Recovery container has network access.'
    }
    $check = (Invoke-BackupCommand (Client-Command $target "SELECT quantity FROM batches; SELECT COUNT(*) FROM stock_movements; SELECT after_data FROM audit_logs; SELECT token_version FROM users; SELECT name FROM products;")).Trim() -split '\r?\n'
    $expected = @('10.125', '1', '{"quantity":10.125}', '7', 'Aceite áéí')
    Assert-True ($check.Count -eq $expected.Count) 'Unexpected recovered query result count.'
    for ($index = 0; $index -lt $expected.Count; $index++) {
        Assert-True ($check[$index] -ceq $expected[$index]) "Recovered value $index does not match the source snapshot."
    }
    Write-Host 'Backup recovery passed: real MySQL, encrypted dump, stock/audit/users/UTF-8, wrong-company/key rejection, tamper/truncation rejection and partial-file cleanup.'
} finally {
    if ($MySqlHome -and $clientArguments.Count -gt 0) {
        # Both names were generated by this test; never read a deletion target from input.
        if ($source -notmatch '^invault_check_[a-f0-9]{10}$' -or $target -notmatch '^invault_recovery_[a-f0-9]{10}$') { throw 'Unsafe test database cleanup.' }
        Execute-Sql '' "DROP DATABASE IF EXISTS $source; DROP DATABASE IF EXISTS $target;"
    }
    if (-not $MySqlHome -and $compose) {
        if ($recoveryContainer) { Invoke-BackupCommand @{File='docker'; Arguments=@('rm', '--force', '--volumes', $recoveryContainer)} | Out-Null }
        Invoke-BackupCommand @{File='docker'; Arguments=($compose + @('down', '--volumes'))} | Out-Null
    }
    $absolute = [IO.Path]::GetFullPath($testRoot)
    if ((Split-Path $absolute -Parent) -cne ([IO.Path]::GetTempPath().TrimEnd([IO.Path]::DirectorySeparatorChar)) -or
        (Split-Path $absolute -Leaf) -notmatch '^invault-backup-test-[a-f0-9]{10}$') { throw 'Unsafe backup test cleanup.' }
    Remove-Item -LiteralPath $absolute -Recurse -Force
}
