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

## Secrets

Every secret is a systemd encrypted credential in `/etc/credstore.encrypted/`.
Deploys never create or prompt for them — the deb's postinst and the units only
read; a missing credential fails the unit with `243/CREDENTIALS`. Placement is
a one-time step of server setup, repeated only when a secret rotates. Each line
prompts for its value (skip the ones already placed):

```bash
sudo -v  # first, or sudo's own prompt fights ask-password for the same terminal
systemd-ask-password -n "db_auth_secret"          | sudo systemd-creds encrypt --name=db_auth_secret          - /etc/credstore.encrypted/db_auth_secret
systemd-ask-password -n "openrouter_api_key"      | sudo systemd-creds encrypt --name=openrouter_api_key      - /etc/credstore.encrypted/openrouter_api_key
systemd-ask-password -n "couchdb_admin_password"  | sudo systemd-creds encrypt --name=couchdb_admin_password  - /etc/credstore.encrypted/couchdb_admin_password
# borg-passphrase is managed by learning-app-admin-setup --rotate-borg (see Borg key)
```

| Credential | Read by |
|---|---|
| `db_auth_secret` | app (`/auth/check` proxy-auth signing) |
| `openrouter_api_key` | app (examples generation) |
| `couchdb_admin_password` | app (server-side CouchDB calls), `learning-app-dictionary-import`, postinst (CouchDB setup, `_global_changes`) |
| `borg-passphrase` | `learning-app-backup-db` |

`--name=` must equal the file name — the blob is bound to it. All blobs are
also bound to this host's `/var/lib/systemd/credential.secret`: they do not
survive a reinstall and cannot be copied to another machine, so every secret
needs an offline copy (password manager).

## Borg key

- Repository encryption is `repokey`: the key sits inside the repository,
  locked by the passphrase. **Losing the passphrase loses every archive** —
  there is no recovery path.
- The passphrase lives as the systemd encrypted credential
  `/etc/credstore.encrypted/borg-passphrase`; the backup unit decrypts it via
  `LoadCredentialEncrypted=` and hands the path to `backup.sh` as
  `BORG_PASSPHRASE_PATH`. It exists nowhere else on the server — keep an
  offline copy (password manager), since the encrypted credential is bound to
  this host's `/var/lib/systemd/credential.secret` and does not survive it.
- Set/rotate: `learning-app-admin-setup --rotate-borg`.
- Verify the key still opens the repository (read-only, safe anytime):
  ```sh
  sudo runuser -u dbmaintainer -- env \
      BORG_REPO="$(grep '^BORG_REPO=' /etc/learning-app/environment | cut -d= -f2-)" \
      BORG_PASSPHRASE="$(sudo systemd-creds --name=borg-passphrase decrypt /etc/credstore.encrypted/borg-passphrase -)" \
      borg list
  ```

## Environment

**Env files:**
- `/etc/environment.d/learning-app.conf` (defaults)
- `/etc/learning-app/environment` (overrides)

**Expected values:**
- `LEARNING_APP__DB_PATH` absolute path
- Example generation uses OpenRouter. Required key via systemd cred or env var:
  - `/etc/credstore.encrypted/openrouter_api_key` or `OPENROUTER_API_KEY`
- Optional OpenRouter env vars:
  - `OPENROUTER_API_URL` — endpoint override
  - `OPENROUTER_MODEL` — primary model id (default: `google/gemini-2.5-flash-lite`)
  - `OPENROUTER_MODELS` — CSV fallback list, e.g. `google/gemini-2.5-flash-lite,deepseek/deepseek-chat-v3.1,openai/gpt-4o-mini`
- `BORG_REPO` for backups

## CouchDB

**Package source:** Apache CouchDB repo.

**Config:** `/opt/couchdb/etc/local.d/10-learning-app.ini`.

**Bindings and access**
- Binds to `127.0.0.1` only (Nginx handles external access).
- `require_valid_user = false` for public reads.
- Proxy auth enabled for future user-database support.

**Admin credential:** `/etc/credstore.encrypted/couchdb_admin_password` — placed per the Secrets section; CouchDB itself learned the same password from its package's debconf prompt at install time, so the two must match.

**dictionary-db**
- Public read-only database for the German dictionary.
- Security: empty members list (anyone can read), admin-only writes.
- Nginx proxy: `/db/dictionary-db/` with rate limiting.
