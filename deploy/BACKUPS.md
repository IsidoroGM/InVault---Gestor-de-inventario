# Encrypted company backups and isolated recovery

Back up each company separately. `backup-company.ps1` takes a consistent InnoDB
snapshot with `mysqldump`, compresses it into ZIP and streams it through
[age](https://github.com/FiloSottile/age). SQL does not pass through PowerShell text
conversion or a plaintext file during backup. Only the public recipient is needed
on the backup host. Keep the private identity in a separate recovery location.

Requirements: PowerShell 7.3+, Docker Compose, a running company database and age.
Install the pinned age 1.3.2 binary, with its official archive SHA-256 verified:

```powershell
./deploy/install-age.ps1 -Destination /opt/invault-tools
# Add the returned age directory to PATH, or pass -AgeExecutable /path/to/age.
```

Create the recovery key on a trusted recovery computer, outside this repository:

```bash
umask 077
age-keygen --output invault-recovery.agekey
age-keygen -y invault-recovery.agekey > backup-recipients.txt
```

Transfer only `backup-recipients.txt` to the backup host. Save the private key in
the organization's protected recovery storage; losing it makes the backups
unrecoverable. Do not use the private key for scheduled backups.

## Create a company backup

```powershell
./deploy/backup-company.ps1 -CompanyId company-a `
  -EnvironmentFile /etc/invault/company-a.env `
  -RecipientFile /etc/invault/backup-recipients.txt `
  -OutputDirectory /var/backups/invault/company-a
```

The environment file must select `invault-company-a` and database
`invault_company_a`. The script checks the rendered Compose project, database,
running container labels and container database name before dumping. It reads
the database credentials inside that company's container, without putting the
password in command arguments or scheduled output.

Successful files have names such as `company-a-<UTC timestamp>-<UUID>.zip.age`.
They are published by an atomic rename only after both dump and encryption exit
successfully. Interrupted/failed dumps remove their `.part` file. The encrypted
archive contains `database.sql` and a manifest recording company, database,
Compose project, UTC timestamp and Git release commit. Pass `-ReleaseCommit <SHA>`
when the installed checkout does not represent the deployed application release.

Use `-ReplicaDirectory /mnt/offsite/company-a` for an already mounted external
destination. The script copies ciphertext, checks SHA-256 and publishes that copy
atomically; a replication failure fails the command and preserves the local backup.
Verify that this mount really resides outside the host. Retention/pruning is
deliberately left to the storage policy; these scripts never delete older backups.

Do not run schema migrations or DDL during a snapshot. `--single-transaction`
provides the consistent snapshot for InnoDB; it does not protect against concurrent
schema changes. The dump uses `--quick`, `--no-tablespaces` and
`--set-gtid-purged=OFF`. Replication/PITR metadata is outside this backup procedure.
See the [MySQL manual](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html).

## Daily automation on a Linux deployment host

The templates under `deploy/systemd/` run at 03:00 UTC with a random delay of up
to 15 minutes. Missed runs are picked up when the host starts. A failing dump,
encryption or replication returns a failed service status for monitoring.

Install PowerShell, Docker and age on the host. Create a dedicated
`invault-backup` service account with Docker access, read access to `/opt/invault`,
its company environment file and the public recipient file, and write access only
to `/var/backups/invault/company-a`. Docker access gives host-administrator power;
limit this account accordingly. Copy the unit files to `/etc/systemd/system`.
Adapt the paths and add `-ReplicaDirectory` to the service if external storage is
mounted. Then activate the company's schedule:

```bash
sudo systemctl daemon-reload
sudo systemctl start invault-backup@company-a.service
sudo systemctl enable --now invault-backup@company-a.timer
systemctl list-timers 'invault-backup@*'
journalctl -u invault-backup@company-a.service
```

These commands are a deployment runbook. No production timer is enabled by
repository development or CI. Monitor failed units and storage usage externally.

## Restore into an isolated recovery container

Only use backups from the organization's trusted backup storage. Encryption
protects confidentiality and detects tampering; a public recipient is not a
signature proving who created an archive.

```powershell
./deploy/restore-company.ps1 -CompanyId company-a `
  -BackupFile /recovery/company-a-<timestamp>-<UUID>.zip.age `
  -IdentityFile /recovery/invault-recovery.agekey
```

The complete age stream is authenticated before any SQL import. The manifest
must match the requested company. Decryption uses an owner-only temporary
directory (Unix mode 0700 or a Windows ACL restricted to the current user), which
is removed afterward. This directory temporarily contains compressed plaintext;
use an encrypted recovery disk and allow space for the decrypted ZIP archive.
Deletion is ordinary filesystem deletion, not a guarantee of secure media erasure.

The restore script creates a **new MySQL 8.4.11 container with no network and no
published ports**, backed by a fresh anonymous volume. It never selects an existing
container or replaces a production database. Failed imports remove only that
new recovery container/volume. Successful recovery returns its name/ID and source
release so an operator can inspect it. For example:

```bash
docker exec <recovery-container> sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM stock_movements; SELECT COUNT(*) FROM audit_logs;"'
```

Verify users, roles, catalogue, decimal stock, movement/audit history and Flyway
history, then validate the application at the recorded release before promotion.
Keep the company's deployment secrets/JWT keys in separate protected recovery
storage: database backups do not include environment files, TLS data or application
images. Plan cutover and newer migrations separately. Once recovery is accepted,
remove the generated recovery container and its anonymous volume with
`docker rm --force --volumes <recovery-container>`.

## Verification

Docker CI runs `deploy/tests/backup-recovery.ps1` with a disposable company database
and exercises the production backup and isolated restore entry points. The local
Windows alternative uses a separate temporary MySQL server:

```powershell
./deploy/test-mysql.ps1 -MySqlHome 'C:\tools\mysql-8.4.13-winx64' `
  -AgeDirectory 'C:\tools\age'
```

The exercise preserves decimal quantities, stock movements, audit snapshots,
users/token versions, saved movement responses, login counters, measurement identity
flags and UTF-8 data after deleting the source stock records. It
also rejects the wrong company, wrong identity, altered/truncated ciphertext and
failed dumps without publishing partial files. This proves the restore procedure;
off-host durability, monitoring, scheduling and recovery objectives still require
acceptance on the chosen deployment host.

For a read-only freshness check, optional replica hash verification and an hourly
host template, see [BACKUP_MONITORING.md](BACKUP_MONITORING.md). These checks require
no private key and complement authenticated recovery drills. No timer is activated
until configured on the chosen host.
