# Infra Verification

All commands are safe to re-run. Skip HTTPS checks if certificates are not issued yet.

## Packages
```sh
dpkg -l certbot python3-certbot-nginx couchdb | grep '^ii'
java -version
```

## Systemd
```sh
systemctl is-enabled learning-app-run.service learning-app-restart.path learning-app-certbot.timer couchdb.service
systemctl status learning-app-run.service
systemctl status learning-app-restart.path
systemctl status learning-app-certbot.timer
systemctl status couchdb.service
```

## Nginx and app
```sh
nginx -t
curl -sf https://sprecha.de/ >/dev/null
```

## CouchDB
```sh
curl -sf http://127.0.0.1:5984/ >/dev/null
curl -sf http://127.0.0.1:5984/dictionary-db >/dev/null
curl -sf http://127.0.0.1:5984/dictionary-db/dictionary-meta >/dev/null
```

## Dictionary import
```sh
systemctl start learning-app-dictionary-import.service
test -f /var/lib/learning-app/dictionary/import-metrics.json
```

## Public dictionary access through Nginx (no auth)
```sh
curl -sf --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/db/dictionary-db/dictionary-meta >/dev/null
```

## Backups (only if configured)
```sh
systemctl is-enabled learning-app-backup-db.timer
systemctl status learning-app-backup-db.timer
```
Borg key dry check — the passphrase credential still opens the repository
(see "Borg key" in `server-configuration.md`):
```sh
sudo runuser -u dbmaintainer -- env \
    BORG_REPO="$(grep '^BORG_REPO=' /etc/learning-app/environment | cut -d= -f2-)" \
    BORG_PASSPHRASE="$(sudo systemd-creds --name=borg-passphrase decrypt /etc/credstore.encrypted/borg-passphrase -)" \
    borg list >/dev/null && echo borg-key-ok
```
