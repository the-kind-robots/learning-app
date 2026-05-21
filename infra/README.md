# Infrastructure

Deployment and local dev environment configs. The app runs on a single Debian server behind nginx with systemd supervision.

## Layout

```
infra/
├── development/              # Local dev environment
│   └── etc/nginx/            # nginx config for sprecha.local (with mkcert TLS)
│
└── production/               # Production Debian package contents
    ├── DEBIAN/               # Package metadata + postinst / postrm scripts
    ├── etc/
    │   ├── environment.d/    # App environment variables (DB path, secrets)
    │   ├── nginx/            # Production nginx vhost (TLS via certbot)
    │   ├── letsencrypt/      # certbot deploy hook (reload nginx on renewal)
    │   ├── systemd/system/   # systemd units and timers (see below)
    │   └── tmpfiles.d/       # Managed directories and permissions
    ├── opt/
    │   └── learning-app/dictionary/
    │       └── import.sh     # Dictionary import script (runs as systemd service)
    └── usr/share/learning-app/
        ├── admin-setup.sh    # First-run admin initialization
        └── backup.sh         # DB backup script (called by backup timer)
```

## systemd units

| Unit                                     | Purpose                                           |
|------------------------------------------|---------------------------------------------------|
| `learning-app-run.service`               | Main app process (http-kit JVM)                   |
| `learning-app-dictionary-import.service` | One-shot: import dictionary SQLite on deploy      |
| `learning-app-backup-db.service`         | DB backup (borgbackup)                            |
| `learning-app-backup-db.timer`           | Daily backup trigger                              |
| `learning-app-certbot.service`           | TLS cert renewal                                  |
| `learning-app-certbot.timer`             | Cert renewal schedule                             |
| `learning-app-restart.path`              | Watch for deploy signal file → restart service    |
| `learning-app-restart.service`           | Performs the restart                              |

## Deployment

The production directory is packaged as a Debian `.deb` (`learning-app-infra`). `postinst` wires up systemd units and nginx on install. See [docs/ops/server-configuration.md](../docs/ops/server-configuration.md) for server setup and [docs/ops/runbook.md](../docs/ops/runbook.md) for operational procedures.

## Local dev

Follow [docs/dev/development-setup.md](../docs/dev/development-setup.md) to configure nginx and mkcert for `sprecha.local`.
