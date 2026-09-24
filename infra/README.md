# Infrastructure

Deployment and local dev environment configs. The app runs on a single Debian server behind nginx with systemd supervision.

## Layout

```
infra/
├── development/              # Local dev environment
│   ├── etc/nginx/            # Dev vhost for sprecha.localhost + the snippet it includes
│   ├── etc/systemd/user/     # systemd user units for the dev stand (see below)
│   └── opt/couchdb/          # CouchDB proxy-auth configuration
│
├── scripts/                  # Repository-side helpers
│   ├── install-dev-stand.sh  # Brings the dev stand up on a machine (see below)
│   └── list-unit-credentials.sh  # Credential names the units load (deploy preflight) + its test
│
├── staging/                  # Production deb in a systemd container + smoke checks (see its README)
│
└── production/               # Production Debian package contents
    ├── DEBIAN/               # Package metadata + postinst / postrm scripts
    ├── etc/
    │   ├── environment.d/    # Non-secret app environment (see below)
    │   ├── nginx/            # Production nginx vhost (TLS via certbot) + the snippet it includes
    │   ├── letsencrypt/      # certbot deploy hook (reload nginx on renewal)
    │   ├── systemd/system/   # systemd units and timers (see below)
    │   ├── sysusers.d/       # System users webapp, dbmaintainer, deployer
    │   └── tmpfiles.d/       # Managed directories and permissions
    ├── opt/
    │   ├── couchdb/etc/local.d/  # Production CouchDB configuration
    │   └── learning-app/
    │       ├── bin/
    │       │   ├── run.sh    # Bridges systemd credentials into env vars, starts the jar
    │       │   └── check-credentials.sh  # Deploy preflight: are the named credentials present
    │       └── dictionary/
    │           └── import.sh # Dictionary import script (runs as systemd service)
    └── usr/share/learning-app/
        ├── admin-setup.sh    # First-run admin initialization
        └── backup.sh         # DB backup script (called by backup timer)
```

## systemd units

| Unit                                     | Purpose                                           |
|------------------------------------------|---------------------------------------------------|
| `learning-app-run.service`               | Main app process (http-kit JVM)                   |
| `learning-app-dictionary-import.service` | One-shot: dictionary into CouchDB, restarts app   |
| `learning-app-backup-db.service`         | DB backup (borgbackup)                            |
| `learning-app-backup-db.timer`           | Daily backup trigger                              |
| `learning-app-certbot.service`           | TLS cert renewal                                  |
| `learning-app-certbot.timer`             | Cert renewal schedule                             |
| `learning-app-restart.path`              | Watch the app jar → restart service               |
| `learning-app-restart.service`           | Performs the restart                              |
| `learning-app-dev-watch.service`         | Dev stand: `shadow-cljs watch app` (user unit)    |
| `learning-app-dev-backend.service`       | Dev stand: the backend on 8083 (user unit)        |

The last two are **user** units under `development/`, not part of the package.
They replace the two terminals the dev stand used to be, so it survives the
session that started it and `systemctl --user status` answers what is running.
`install-dev-stand.sh` installs and enables them; running them day to day is
[docs/dev/mobile-pwa-testing.md](../docs/dev/mobile-pwa-testing.md).

## Secrets and environment

Secrets are systemd encrypted credentials in `/etc/credstore.encrypted`, one
file per name. Units load them with `LoadCredentialEncrypted=`, and
`bin/run.sh` exports them as the env vars the app reads. They do not belong in
an environment file; `run.sh` overwrites a value that leaked into one. The
deploy workflow lists the names with `scripts/list-unit-credentials.sh` and checks
them on the server with `bin/check-credentials.sh` before installing.

`etc/environment.d/learning-app.conf` holds only non-secret settings:
`LEARNING_APP__DB_PATH`, read by the backend, and
`LEARNING_APP__ENVIRONMENT`, which no code reads today.

## Deployment

The production directory is packaged as a Debian `.deb` (`learning-app-infra`). `postinst` wires up systemd units and nginx on install. See [docs/ops/server-configuration.md](../docs/ops/server-configuration.md) for server setup and [docs/ops/runbook.md](../docs/ops/runbook.md) for operational procedures.

## Local dev

One command installs everything under `development/` onto a machine and reports
what it found:

```bash
infra/scripts/install-dev-stand.sh
```

It is the single copy of the bring-up list — the vhost and the snippet it
includes (nginx refuses to start when an included file is missing), the CouchDB
configuration and databases, the user units and their lingering. It is safe to
re-run, shows any file that differs before replacing it, and asks before every
step that needs root. Credentials and logins are named, never invented.

The stand is plain http on `sprecha.localhost`; the `sprecha.local` mkcert
certificates it used to need are gone. Full guide:
[docs/dev/development-setup.md](../docs/dev/development-setup.md).
