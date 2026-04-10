# Production Infrastructure Reference

Static reference only. Procedures live in `docs/ops/runbook.md` and `docs/ops/verification.md`.

## Domains

**Registrar:** namecheap.com (owner: @u473t8)

## Hosting

**Provider:** hetzner.com (owner: @u473t8)

## Nginx

**Config:** `/etc/nginx/sites-available/learning-app.conf` (symlinked to `/etc/nginx/sites-enabled/learning-app.conf`).

**TLS cert path (root-only):** `/etc/nginx/ssl/sprecha.de/`.

**Why root-only certs**
- Certbot writes configs that point to `/etc/letsencrypt/live/...`.
- Those files are root-owned and too permissive to expose to worker users.
- We copy certs into `/etc/nginx/ssl/sprecha.de` and reload nginx on renew.

**Automation**
- Deploy hook: `/etc/letsencrypt/renewal-hooks/deploy/learning-app-nginx.sh`.
- Certbot timer: `learning-app-certbot.service` and `learning-app-certbot.timer`.

## Users

**System users (created by sysusers):**
- `webapp` (home: `/opt/learning-app`) runs the app.
- `dbmaintainer` (home: `/var/lib/dbmaintainer`) runs backups.

**Deployer user:** `deployer` (home: `/home/deployer`), SSH-only for CI deploys.

## Services

**App runtime**
- Unit: `/etc/systemd/system/learning-app-run.service`.
- Jar: `/opt/learning-app/learning-app.jar`.

**Restart on deploy**
- Units: `/etc/systemd/system/learning-app-restart.path` and `/etc/systemd/system/learning-app-restart.service`.

**Backups**
- Units: `learning-app-backup-db.service` and `learning-app-backup-db.timer`.
- Script: `/var/lib/dbmaintainer/backup.sh` (copied from `/usr/share/learning-app/backup.sh`).
- Output: `/var/backups/learning-app`.

**Cert renewal**
- Units: `learning-app-certbot.service` and `learning-app-certbot.timer`.

## Environment

**Env files:**
- `/etc/environment.d/learning-app.conf` (defaults)
- `/etc/learning-app/environment` (overrides)

**Expected values:**
- `LEARNING_APP_DB_PATH` absolute path
- OpenRouter API key via systemd creds (`/etc/credstore.encrypted/openai_api_key`) or `OPENROUTER_API_KEY`
  Legacy fallback during rollout: `OPENAI_API_KEY`
- `BORG_REPO` for backups

## CouchDB

**Package source:** Apache CouchDB repo.

**Config:** `/opt/couchdb/etc/local.d/10-learning-app.ini`.

**Bindings and access**
- Binds to `127.0.0.1` only (Nginx handles external access).
- `require_valid_user = false` for public reads.
- Proxy auth enabled for future user-database support.

**Admin credential:** `/etc/credstore.encrypted/couchdb_admin_password`.

**dictionary-db**
- Public read-only database for the German dictionary.
- Security: empty members list (anyone can read), admin-only writes.
- Nginx proxy: `/db/dictionary-db/` with rate limiting.
