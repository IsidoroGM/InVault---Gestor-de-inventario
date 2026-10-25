# Identified application releases

`build-release.ps1` builds backend and frontend from a **clean committed checkout**.
It requires Docker and Git, without production credentials or a company database.
It does not deploy containers, migrate data, push images or publish a registry.

```powershell
$release = ./deploy/build-release.ps1
$release
```

The helper reads the full commit SHA from the source it builds and rejects tracked
or non-ignored untracked changes. It passes that SHA to both Dockerfiles as
`INVAULT_BUILD_REVISION`; the images record it in the OCI revision label. Tags are
`invault-backend:<SHA>` and `invault-frontend:<SHA>`. Code images can be reused across
companies; each company still has its own configuration and database.

Only this invocation's temporary image tags are removed. An existing release tag
pointing to another image ID is not replaced. A rebuild after cache/base-image
changes may be rejected; retain the recorded artifacts or create a new committed
release. This identifies sources and preserves local artifacts; builds are not
claimed to be reproducible or cryptographically signed.

The ignored `releases/` directory receives two files: a `.release.json` descriptor
with revision, image references and image IDs, and a `.compose.json` overlay
selecting those tags with `pull_policy: never`. Different existing files are not
overwritten. Keep descriptors and actual images with your recovery material.
Files use temporary writes and rename; no release result is returned until both
are available.

## Deploy the already-built images

Validate the company and back up its existing database before updating. Always
use the same project/environment and the release overlay for `up`:

```powershell
./deploy/check-company.ps1 -CompanyId company-a `
  -EnvironmentFile .env.company-company-a -ConfigurationOnly
# For an existing company, run backup-company.ps1 before updating.
docker compose --project-name invault-company-a --env-file .env.company-company-a `
  --file compose.prod.yml --file $release.ComposeFile up --detach --no-build --wait
./deploy/check-company.ps1 -CompanyId company-a `
  -EnvironmentFile .env.company-company-a -ExpectedRevision $release.Revision
```

`--no-build` retains the selected artifacts. The diagnostic compares running
backend/frontend revision labels and reports image IDs; compare IDs with the
descriptor for artifact verification. It does not undo migrations or authorize a
downgrade. Follow backup/recovery instructions when changing schema versions.

Images are local. For another host, securely transfer/load the recorded images
or configure an authenticated registry and corresponding image-reference policy.
The helper does not configure/publish a registry or install on a remote host.

## Accurate backup metadata

`backup-company.ps1` derives `releaseCommit` from the deployed backend image label.
The current Git checkout is not used as the installed version: checking out new
sources before backup must not rewrite history.

For a legacy/unlabelled image or absent backend, supply
`-ReleaseCommit <verified-deployed-SHA>`. A supplied SHA conflicting with a known
image revision is rejected, as are short SHAs. A stopped backend container may
still identify the deployed version while its database is backed up.

Direct `docker compose build` permits `unversioned` development images. They
require an explicit verified identity for backup; production should use the
identified helper and overlay. CI builds with the helper, checks both running
revision labels and takes an encrypted backup without a manual release argument,
then verifies the result and encrypted manifest against the image revision.
