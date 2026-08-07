# Production Runbook

Goal: get a production server to a running state with the shortest, sequential steps possible.
All steps are idempotent unless marked NON-IDEMPOTENT.

## CI and deploy entrypoints

Testing is automatic. Deployment is manual.

- Pull requests to `master` run `.github/workflows/integration.yml`.
- Pushes to `master` also run `.github/workflows/integration.yml` for post-merge visibility.
- Production deploys are manual via workflow dispatch:
  - `.github/workflows/deploy-infra.yml`
  - `.github/workflows/deploy-app.yml`
  - `.github/workflows/deploy-dictionary.yml`

Recommended manual order when infra changed:
1. Run deploy infra.
2. Run deploy app.
3. Run deploy dictionary.

1) Install or upgrade the infra package (idempotent).
```sh
sudo dpkg -i learning-app-infra.deb || sudo apt-get -f install
```
Notes: postinst is noninteractive. It configures users, tmpfiles, nginx config, certbot hook, and systemd units. It skips starting the app and backups if secrets are missing.

2) Upload the new app artifact and atomically replace it (idempotent).
```sh
mv -f /opt/learning-app/learning-app.jar.tmp /opt/learning-app/learning-app.jar
```
This triggers `learning-app-restart.path`, which restarts `learning-app-run.service`.

3) Upload dictionary artifact/data and run import (idempotent).
```sh
systemctl start learning-app-dictionary-import.service
```
The import validates input checksums and writes `/var/lib/learning-app/dictionary/import-metrics.json`.

## Infra deploy failed: manual recovery

If infra deployment fails in GitHub Actions, fix infra first and then rerun manual deploy workflows in order (infra -> app -> dictionary).

CI prerequisite: deploy SSH user must have passwordless sudo for deploy commands (`dpkg`, `apt-get`, `systemctl`, `nginx`).

Quick fix when logs show `sudo: a terminal is required`:
```sh
sudo visudo -f /etc/sudoers.d/learning-app-deployer-ci
# add exactly this line and save:
# deployer ALL=(root) NOPASSWD: /usr/bin/dpkg, /usr/bin/apt-get, /usr/bin/systemctl, /usr/sbin/nginx
sudo chmod 440 /etc/sudoers.d/learning-app-deployer-ci
sudo visudo -cf /etc/sudoers.d/learning-app-deployer-ci
sudo -u deployer sudo -n systemctl --version >/dev/null
```

1) Inspect server state.
```sh
sudo systemctl status couchdb.service --no-pager
sudo journalctl -u couchdb.service -n 200 --no-pager
sudo nginx -t
sudo systemctl cat learning-app-dictionary-import.service
```

2) Fix issues directly on the server (packages, config, credentials, permissions).

3) Re-run deployment.
```sh
# dispatch deploy-infra, then deploy-app, then deploy-dictionary
```

4) Verify after recovery.
```sh
curl -sf http://127.0.0.1:5984/ >/dev/null
curl -sf --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/db/dictionary-db/dictionary-meta >/dev/null
```

## Server database home

The server SQLite lives at `/var/lib/learning-app/db.sqlite` (`LEARNING_APP__DB_PATH`
from environment.d; the directory is systemd's `StateDirectory`). Builds carrying
#201 adopt a legacy `/opt/learning-app/app.db` on first boot: move it, log
`database-adopted`, and refuse to clobber an existing target (`legacy-database-left-behind`
in the log means both files exist — resolve by hand, the running app is on the target).

Rollback caveat: redeploying a pre-#201 jar after adoption starts an empty
database in `/opt` — tokens then 401 until a #201+ jar is redeployed. The data
itself stays safe at the new home.

## Restore from backup

Archives named `app-<timestamp>` carry both stores: the SQLite snapshot (at
its backup path, `var/backups/learning-app/db.sqlite`) and `/var/lib/couchdb`.
Restore them together, from the same archive — an account row and its userdb
must come from one moment, or you manufacture the orphan states #182/#205
exist to catch. Older `db-*.sqlite` archives predate #206 and hold SQLite only.

Borg runs as `dbmaintainer` (the repository owner) throughout — root would
leave root-owned lock/cache state in the repository and break the backup
timer. The passphrase and its handling: see "Borg key" in
`docs/ops/server-configuration.md`.

```sh
sudo runuser -u dbmaintainer -- env BORG_REPO=... BORG_PASSPHRASE=... borg list   # pick an archive
sudo systemctl stop learning-app-run.service couchdb.service
# extract into a dbmaintainer-writable scratch dir
sudo runuser -u dbmaintainer -- env BORG_REPO=... BORG_PASSPHRASE=... \
    sh -c 'cd /var/tmp/restore && borg extract ::app-<timestamp>'
# SQLite: snapshot to the live path; ownership webapp (borg extracted as
# dbmaintainer, so ownership does NOT come back on its own). Stale -wal/-shm
# from the old database MUST go — SQLite would replay them into the snapshot.
sudo install -o webapp -g webapp -m 660 \
    /var/tmp/restore/var/backups/learning-app/db.sqlite /var/lib/learning-app/db.sqlite
sudo rm -f /var/lib/learning-app/db.sqlite-wal /var/lib/learning-app/db.sqlite-shm
# CouchDB: replace the tree wholesale (an overlay mixes old state into the
# restore), ownership couchdb:couchdb.
sudo rm -rf /var/lib/couchdb
sudo cp -a /var/tmp/restore/var/lib/couchdb /var/lib/couchdb
sudo chown -R couchdb:couchdb /var/lib/couchdb
# CouchDB first, answering before the app starts — the app's boot
# reconciliation (orphan report) needs to enumerate databases.
sudo systemctl start couchdb.service
until curl -sf http://127.0.0.1:5984/ >/dev/null; do sleep 1; done
sudo systemctl start learning-app-run.service
```

Before calling it done: `/auth/check` answers 200 for a known token, that
account's userdb serves its documents through `/db/`, and the boot
`orphan-userdbs` report in the app journal is clean. This procedure is
exercised end to end by `infra/staging/restore-drill.sh` — if reality and
this section disagree, fix whichever is wrong in the same PR.

## Manual admin steps (bootstrap + secrets + one-off ops)

Run the admin setup script (idempotent, safe to re-run).
```sh
sudo learning-app-admin-setup --bootstrap --issue-cert
```
Notes:
- Command path: `/usr/local/bin/learning-app-admin-setup` (symlink to `/usr/share/learning-app/admin-setup.sh`).
- The script prompts for missing secrets, deployer SSH key, and `BORG_REPO`.
- If the command is missing, run the CI step that installs the infra deb first.
- `--bootstrap` installs base packages and the CouchDB repo. NON-IDEMPOTENT: CouchDB package prompts once for admin setup.
- On changes, the script reloads nginx when certs are issued and restarts the app when OpenAI credentials change.
- For GitHub-hosted runners, use SSH key restrictions without `from=`:
  `no-agent-forwarding,no-port-forwarding,no-pty,no-X11-forwarding`.
- If DNS/ports are not ready, run without `--issue-cert` and re-run later with the flag.
- Secret rotation is explicit: `--rotate-openai`, `--rotate-couchdb`, `--rotate-borg`.
Examples:
```sh
sudo learning-app-admin-setup --bootstrap --issue-cert
sudo learning-app-admin-setup
sudo learning-app-admin-setup --rotate-openai
```

Verification: see `docs/ops/verification.md`.
