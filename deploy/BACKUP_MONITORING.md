# Backup freshness and replica monitoring

`check-backups.ps1` is a read-only command for one company's published encrypted
backups. It does not need MySQL, Docker, an encryption private key or mail access.
It reports JSON and exits **0** when its configured checks pass, **2** when a
backup/replica check fails, and nonzero on invalid input or an operational error.

```powershell
./deploy/check-backups.ps1 -CompanyId company-a `
  -BackupDirectory /var/backups/invault/company-a -MaxAgeHours 30 `
  -ReplicaDirectory /mnt/backup-replica/company-a
```

The command selects the latest complete `company-id-UTC-timestamp-UUID.zip.age`
artifact for the exact company. It ignores another company's files and `.part`
files. Its age comes from the timestamp created by the backup tool, not file
modification time, so copying or touching an old backup does not make it recent.
Dates more than five minutes in the future fail. The latest selected artifact
must be readable, exceed 100 bytes and start with the age v1 header; a truncated
latest artifact does not silently fall back to an older one.

When a replica directory is configured, the latest snapshot must also exist there
with the same size and SHA-256. Comparing full hashes reads both files and may take
time on large or remote storage. The same source directory is rejected as its own
replica. Different directories do not prove different disks or hosts: the operator
must mount and verify the intended external destination.

The JSON's `healthy` field refers only to these checks. Filename times and headers
are structural evidence, not authenticated backup metadata. Matching hashes detect
differences between the two artifacts, not coordinated corruption of both. Periodic
authenticated recovery and SQL checks in [BACKUPS.md](BACKUPS.md) remain necessary.
No source file, backup, replica or encryption key is changed or deleted.

## Optional host timer

Templates in `deploy/systemd/invault-backup-check@.*` run hourly with a two-minute
random delay and a 30-hour maximum age, allowing margin around the daily backup.
They are supplied as source and are not activated by the application or CI.
On the chosen Linux host, after a first successful backup and directory setup:

```bash
sudo install -m 644 deploy/systemd/invault-backup-check@.service /etc/systemd/system/
sudo install -m 644 deploy/systemd/invault-backup-check@.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now invault-backup-check@company-a.timer
sudo systemctl start invault-backup-check@company-a.service
sudo journalctl -u invault-backup-check@company-a.service --no-pager
```

The service uses the existing `invault-backup` account and reads the company's
backup directory. A missing directory or stale/missing artifact fails the service.
Connect failed-unit/exit-status monitoring to the host's chosen alert system;
these templates do not send notifications themselves.

To require a replica, use a systemd service override that replaces `ExecStart`
with the same command plus `-ReplicaDirectory` for the company's actual mounted
destination. Configure `backup-company.ps1 -ReplicaDirectory` to create that copy.
The base backup and monitoring templates do not assume an external mount exists.

## Verification

`deploy/tests/backup-status.ps1` tests empty/missing directories, wrong companies,
stale files with recent modification times, partial files, future timestamps,
truncation, missing and same-size altered replicas, same-directory rejection and
command-line exit codes. It uses synthetic header fixtures; they are not claimed
to be recoverable backups. The encrypted MySQL recovery test additionally checks
the monitor against an actual age artifact and, in Docker CI, its replica.
