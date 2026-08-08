#!/usr/bin/env bash
# Runs INSIDE the staging container. One PASS/FAIL line per assertion,
# nonzero exit if anything failed.
set -u

BORG_PASS="staging-borg-pass"   # must match run.sh
COUCH_PASS="3434"               # must match run.sh
BORG_REPO="/var/lib/dbmaintainer/borg-repo"
DB="/var/lib/learning-app/db.sqlite"

fails=0

check() {
    local name="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        echo "PASS ${name}"
    else
        echo "FAIL ${name}"
        fails=$((fails + 1))
    fi
}

http_code() {
    curl -k -s -o /dev/null -w '%{http_code}' "$@"
}

# Units under their production sandboxes.
for unit in nginx.service couchdb.service learning-app-run.service \
            learning-app-restart.path learning-app-certbot.timer \
            learning-app-backup-db.timer; do
    check "unit active: ${unit}" systemctl is-active --quiet "${unit}"
done

# CouchDB admin: the credstore password authenticates, and the ini postinst
# wrote during the drift scenario (run.sh, GH-252) holds a hash, not plaintext
# -- CouchDB rewrites the value on startup.
ADMIN_INI="/opt/couchdb/etc/local.d/99-admin-password.ini"
check "couchdb: credstore password is the admin password" \
    test "$(http_code -u "admin:${COUCH_PASS}" http://127.0.0.1:5984/_node/_local/_config/admins)" = 200
check "couchdb: admin ini exists after drift realignment" \
    test -f "${ADMIN_INI}"
check "couchdb: admin ini holds a hash, not the plaintext password" \
    grep -Eq '^admin *= *-' "${ADMIN_INI}"

# nginx answers inside the container (self-signed cert, hence -k).
check "nginx: https:// answers 200" \
    test "$(http_code --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/)" = 200
check "nginx: http:// redirects 301" \
    test "$(http_code --resolve sprecha.de:80:127.0.0.1 http://sprecha.de/)" = 301

# Auth boundary: no cookie means 401 — at the app and through nginx's
# auth_request on /db/ (nginx marks /auth/check itself `internal`).
check "app: /auth/check without cookie is 401" \
    test "$(http_code http://127.0.0.1:8083/auth/check)" = 401
check "nginx: /db/ without cookie is 401" \
    test "$(http_code --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/db/anything)" = 401

# Migrations ran against the configured database path.
check "migrations: schema_migrations has rows" \
    bash -c "test \"\$(sqlite3 ${DB} 'SELECT count(*) FROM schema_migrations;')\" -ge 1"

# Adoption (#209): the legacy /opt/learning-app/app.db pre-placed before the
# first service start must have moved into the configured path.
check "adoption: database-adopted in the journal" \
    bash -c 'journalctl -u learning-app-run.service --no-pager | grep -q database-adopted'
check "adoption: marker row survived the move" \
    bash -c "test \"\$(sqlite3 ${DB} 'SELECT note FROM staging_marker;' 2>/dev/null)\" = legacy"
check "adoption: legacy file retired" \
    bash -c 'test ! -e /opt/learning-app/app.db'

# Backup through the real unit (its sandbox included), against the local
# borg repository run.sh initialized.
check "backup: learning-app-backup-db.service runs clean" \
    systemctl start learning-app-backup-db.service

borg_ls() {
    runuser -u dbmaintainer -- env \
        BORG_REPO="${BORG_REPO}" BORG_PASSPHRASE="${BORG_PASS}" borg "$@"
}
archive="$(borg_ls list --short 2>/dev/null | tail -1)"
check "backup: archive exists" test -n "${archive}"
contents="$(borg_ls list "::${archive}" 2>/dev/null)"
check "backup: archive carries the sqlite store" \
    bash -c "grep -q 'db.sqlite' <<< '${contents}'"
check "backup: archive carries the couchdb store" \
    bash -c "grep -q 'var/lib/couchdb' <<< '${contents}'"

if [ "${fails}" -gt 0 ]; then
    echo "SMOKE: ${fails} assertion(s) failed"
    exit 1
fi
echo "SMOKE: all assertions passed"
