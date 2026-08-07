#!/usr/bin/env bash
# Restore drill (GH-191). Runs INSIDE the staging container, after smoke.sh:
# seed real data, back it up, destroy both stores, restore from the borg
# archive by the runbook's procedure, and prove the restored system coheres —
# token authenticates, userdb serves its doc, orphan report is clean.
# The runbook's "Restore from backup" section is the spec; this script keeps
# that document honest. One PASS/FAIL line per assertion, nonzero exit on FAIL.
set -u

BORG_PASS="staging-borg-pass"        # must match run.sh
BORG_REPO="/var/lib/dbmaintainer/borg-repo"
COUCH_PASS="3434"                    # must match the debconf preseed in Dockerfile
COUCH="http://127.0.0.1:5984"
DB="/var/lib/learning-app/db.sqlite"
SCRATCH="/var/tmp/learning-app-restore-drill"

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

borg_dbm() {
    runuser -u dbmaintainer -- env \
        BORG_REPO="${BORG_REPO}" BORG_PASSPHRASE="${BORG_PASS}" borg "$@"
}

echo "== drill: seed =="
# An invite grant, planted straight into the store the way mint-grant! would;
# provisioning then runs through the real endpoint so the account row and its
# userdb are created by the app itself.
invite="$(openssl rand -hex 20)"
invite_sha="$(printf '%s' "${invite}" | sha256sum | cut -d' ' -f1)"
expires_ms=$((($(date +%s) + 3600) * 1000))
sqlite3 "${DB}" \
    "INSERT INTO grants (sha256, expires_at) VALUES ('${invite_sha}', ${expires_ms});"

# Explicit JSON content type: without it curl sends form-urlencoded and the
# parameters interceptor consumes the body before the handler can parse it.
provision="$(curl -s -X POST http://127.0.0.1:8083/api/identity/provision \
    -H 'Content-Type: application/json' -d "{\"invite\":\"${invite}\"}")"
token="$(grep -oP '"token":"\K[0-9a-f]+' <<< "${provision}")"
account_id="$(grep -oP '"id":\K[0-9]+' <<< "${provision}")"
check "seed: provisioning returned an account" \
    bash -c "test -n '${token}' && test -n '${account_id}'"
userdb="userdb-${account_id}"

payload="drill-$(openssl rand -hex 8)"
check "seed: doc written to the userdb" \
    bash -c "curl -sf -u admin:${COUCH_PASS} -X PUT ${COUCH}/${userdb}/drill-doc \
        -d '{\"payload\":\"${payload}\"}' >/dev/null"
check "seed: /auth/check accepts the token" \
    test "$(http_code -H "Cookie: auth-token=${token}" http://127.0.0.1:8083/auth/check)" = 200

echo "== drill: backup =="
check "backup: learning-app-backup-db.service runs clean" \
    systemctl start learning-app-backup-db.service
archive="$(borg_dbm list --short 2>/dev/null | tail -1)"
check "backup: archive exists" test -n "${archive}"

echo "== drill: destroy both stores =="
curl -sf -u "admin:${COUCH_PASS}" -X DELETE "${COUCH}/${userdb}" >/dev/null
check "destroy: userdb gone from couchdb" \
    test "$(http_code -u "admin:${COUCH_PASS}" "${COUCH}/${userdb}")" = 404
rm -f "${DB}" "${DB}-wal" "${DB}-shm"
check "destroy: token no longer authenticates" \
    bash -c "test \"\$(http_code -H 'Cookie: auth-token=${token}' http://127.0.0.1:8083/auth/check)\" != 200"

echo "== drill: restore (runbook procedure) =="
# Stop services before touching either store.
systemctl stop learning-app-run.service couchdb.service

# Extract as dbmaintainer — the repository owner. Running borg as root would
# leave root-owned lock/cache state in the repository and break later timer
# runs; the flip side is that a non-root extract cannot restore file
# ownership, so both stores are chowned explicitly below (runbook says so).
rm -rf "${SCRATCH}"
install -d -o dbmaintainer -g dbmaintainer "${SCRATCH}"
check "restore: borg extract ${archive}" \
    runuser -u dbmaintainer -- env \
        BORG_REPO="${BORG_REPO}" BORG_PASSPHRASE="${BORG_PASS}" \
        bash -c "cd '${SCRATCH}' && borg extract \"::${archive}\""

# SQLite: the archive carries the snapshot at var/backups/learning-app/ —
# place it at the live path, owned by the app user. Stale -wal/-shm from the
# destroyed database must not survive: SQLite would replay them into the
# restored snapshot.
install -o webapp -g webapp -m 660 \
    "${SCRATCH}/var/backups/learning-app/db.sqlite" "${DB}"
rm -f "${DB}-wal" "${DB}-shm"

# CouchDB: replace the tree wholesale — an overlay would leave the destroyed
# state's newer files mixed into the restored one.
rm -rf /var/lib/couchdb
cp -a "${SCRATCH}/var/lib/couchdb" /var/lib/couchdb
chown -R couchdb:couchdb /var/lib/couchdb

# CouchDB first, and answering, then the app: boot reconciliation reports
# orphans only when it can enumerate databases.
systemctl start couchdb.service
for _ in $(seq 1 30); do
    curl -sf -o /dev/null "${COUCH}/" && break
    sleep 1
done
systemctl start learning-app-run.service

up=0
for _ in $(seq 1 90); do
    if curl -sf -o /dev/null http://127.0.0.1:8083/; then up=1; break; fi
    sleep 1
done
check "restore: app answers after restore" test "${up}" = 1

echo "== drill: coherence =="
check "coherence: /auth/check accepts the seeded token" \
    test "$(http_code -H "Cookie: auth-token=${token}" http://127.0.0.1:8083/auth/check)" = 200
doc="$(curl -k -s --resolve sprecha.de:443:127.0.0.1 \
    -H "Cookie: auth-token=${token}" \
    "https://sprecha.de/db/${userdb}/drill-doc")"
check "coherence: userdb serves the seeded doc through nginx" \
    bash -c "grep -q '${payload}' <<< '${doc}'"
# Boot reconciliation (#205) logs :reconciliation/orphan-userdbs with the
# orphan vector; a clean restore reports none. Only the latest boot counts.
orphan_line="$(journalctl -u learning-app-run.service --no-pager \
    | grep 'orphan-userdbs' | tail -1)"
check "coherence: orphan report present after restore" \
    test -n "${orphan_line}"
check "coherence: orphan report is clean" \
    bash -c "! grep -q 'userdb-[0-9]' <<< '${orphan_line}'"

rm -rf "${SCRATCH}"

if [ "${fails}" -gt 0 ]; then
    echo "DRILL: ${fails} assertion(s) failed"
    exit 1
fi
echo "DRILL: all assertions passed"
