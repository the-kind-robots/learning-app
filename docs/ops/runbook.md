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

## Restore from backup

Archives named `app-<timestamp>` carry both stores: the SQLite snapshot and
`/var/lib/couchdb`. Restore them together, from the same archive — an account
row and its userdb must come from one moment, or you manufacture the orphan
states #182/#205 exist to catch. `borg list ::` to pick, `borg extract` into a
scratch dir, then: stop services, put the sqlite file at
`/var/lib/learning-app/db.sqlite`, the couch tree at `/var/lib/couchdb`
(ownership `couchdb:couchdb`), start, and check `/auth/check` with a known
token before calling it done. Older `db-*.sqlite` archives predate #206 and
hold SQLite only.

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
