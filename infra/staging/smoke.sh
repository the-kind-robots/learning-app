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
            learning-app-backup-db.timer learning-app-cdn-ranges.timer; do
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
    test "$(http_code --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/db/userdb-1/)" = 401

# The proxy-auth boundary (GH-304). CouchDB trusts X-Auth-CouchDB-* by
# construction, so nginx must drop whatever a client sends. No response reveals
# the headers an upstream received -- CouchDB answers a spoofed request exactly
# as it answers an anonymous one -- so the app steps aside and a listener reads
# the request nginx actually forwards.
upstream_clean_of_couch_headers() {
    local capture=/tmp/proxy-auth-capture nc_pid
    systemctl stop learning-app-run.service
    timeout 10 nc -l 127.0.0.1 8083 > "${capture}" &
    nc_pid=$!
    sleep 1
    curl -k -s -m 3 --resolve sprecha.de:443:127.0.0.1 \
        -H 'X-Auth-CouchDB-UserName: attacker' \
        -H 'X-Auth-CouchDB-Roles: _admin' \
        -H 'X-Auth-CouchDB-Token: forged' \
        https://sprecha.de/ >/dev/null 2>&1
    kill "${nc_pid}" 2>/dev/null
    wait "${nc_pid}" 2>/dev/null
    # Put the stand back the way it was found: the assertions after this one --
    # and the restore drill after them -- expect a JVM that already answers.
    systemctl start learning-app-run.service
    for _ in $(seq 1 90); do
        curl -sf -o /dev/null http://127.0.0.1:8083/ && break
        sleep 1
    done
    # A capture that never happened must not read as clean.
    grep -qi '^GET ' "${capture}" && ! grep -qi 'x-auth-couchdb' "${capture}"
}
check "nginx: a client's proxy-auth header does not reach the upstream" \
    upstream_clean_of_couch_headers

# Named on its own, so a stand left broken by the capture above is reported
# here instead of surfacing as an unrelated failure further down.
app_answers_again() { test "$(http_code http://127.0.0.1:8083/)" = 200; }
check "app: answers again after the proxy-auth capture" app_answers_again

# /db/ passes only what a one-shot PouchDB pass asks (GH-796). An account of
# its own, provisioned through the real endpoint from a planted invite, as the
# restore drill does.
invite="$(openssl rand -hex 20)"
sqlite3 "${DB}" "INSERT INTO grants (sha256, expires_at) VALUES \
    ('$(printf '%s' "${invite}" | sha256sum | cut -d' ' -f1)', $((($(date +%s) + 3600) * 1000)));"
provision="$(curl -s -X POST http://127.0.0.1:8083/api/identity/provision \
    -H 'Content-Type: application/json' -d "{\"invite\":\"${invite}\"}")"
token="$(grep -oP '"token":"\K[0-9a-f]+' <<< "${provision}")"
userdb="userdb-$(grep -oP '"id":\K[0-9]+' <<< "${provision}")"
check "db proxy: an account to replicate with" test -n "${token}"

db() {
    local method="$1" path="$2"
    shift 2
    http_code --resolve sprecha.de:443:127.0.0.1 -X "${method}" \
        -H "Cookie: auth-token=${token}" -H 'Content-Type: application/json' \
        "$@" "https://sprecha.de/db/${path}"
}

check "db proxy: server root"        test "$(db GET '')" = 200
check "db proxy: database info"      test "$(db GET "${userdb}/")" = 200
check "db proxy: plain change feed" \
    test "$(db GET "${userdb}/_changes?style=all_docs&since=0&limit=100&include_docs=true")" = 200
check "db proxy: _revs_diff"         test "$(db POST "${userdb}/_revs_diff" -d '{}')" = 200
check "db proxy: _bulk_docs" \
    test "$(db POST "${userdb}/_bulk_docs" -d '{"docs":[],"new_edits":false}')" = 201
check "db proxy: _bulk_get"          test "$(db POST "${userdb}/_bulk_get?revs=true" -d '{"docs":[]}')" = 200
check "db proxy: checkpoint write" \
    test "$(db PUT "${userdb}/_local/smoke-checkpoint" -d '{"session_id":"smoke","history":[]}')" = 201
check "db proxy: checkpoint read"    test "$(db GET "${userdb}/_local/smoke-checkpoint")" = 200
check "db proxy: a document read reaches CouchDB" \
    test "$(db GET "${userdb}/vocab:smoke")" = 404

for refused in "POST ${userdb}/_find" "POST _replicate" "POST ${userdb}/_purge" \
               "GET ${userdb}/_design/smoke" "GET ${userdb}/vocab:smoke/attachment" \
               "GET _all_dbs" "POST _session" "PUT userdb-999999/" "DELETE ${userdb}/" \
               "GET ${userdb}/_changes?feed=longpoll" "GET ${userdb}/_changes?feed=continuous"; do
    check "db proxy: refuses ${refused}" \
        test "$(db ${refused% *} "${refused#* }")" = 403
done
check "db proxy: a refusal comes before the auth check" \
    test "$(http_code --resolve sprecha.de:443:127.0.0.1 https://sprecha.de/db/_all_dbs)" = 403
check "db proxy: refuses a compressed body" \
    test "$(db POST "${userdb}/_bulk_docs" -H 'Content-Encoding: gzip' -d '{}')" = 415
head -c 10000 /dev/zero | tr '\0' ' ' > /tmp/body-10k
head -c 1100000 /dev/zero | tr '\0' ' ' > /tmp/body-1100k
check "db proxy: refuses a checkpoint of 10 KB" \
    test "$(db PUT "${userdb}/_local/smoke-checkpoint" --data-binary @/tmp/body-10k)" = 403
check "db proxy: refuses a checkpoint without a length" \
    test "$(db PUT "${userdb}/_local/smoke-checkpoint" -H 'Transfer-Encoding: chunked' -d '{}')" = 403
check "db proxy: refuses a body over 1 MB" \
    test "$(db POST "${userdb}/_bulk_docs" --data-binary @/tmp/body-1100k)" = 413

# Twenty slow uploads hold the address's connections while nginx reads their
# bodies; the next request is over the limit. curl itself is the background
# job, not the db helper: killing a helper's subshell would leave its curl
# holding a connection into the restore drill.
connection_limit_answers_429() {
    local body=/tmp/slow-body pids=() code
    head -c 40000 /dev/zero | tr '\0' ' ' > "${body}"
    for _ in $(seq 1 20); do
        curl -k -s -o /dev/null --resolve sprecha.de:443:127.0.0.1 -X POST \
            -H "Cookie: auth-token=${token}" -H 'Content-Type: application/json' \
            --limit-rate 2k --data-binary "@${body}" \
            "https://sprecha.de/db/${userdb}/_bulk_docs" &
        pids+=($!)
    done
    sleep 3
    code="$(db GET "${userdb}/")"
    kill "${pids[@]}" 2>/dev/null
    wait "${pids[@]}" 2>/dev/null
    test "${code}" = 429
}
check "db proxy: connections over the per-address limit get 429" connection_limit_answers_429
check "db proxy: a throttled answer is not logged" \
    bash -c "! grep -q '\" 429 ' /var/log/nginx/access.log"

# The client behind the CDN (GH-801). The real lists are the CDN's; here the
# refresh unit reads a list naming 127.0.0.2, which stands in for an edge:
# Linux routes all of 127/8 to loopback, so curl can send from it.
EDGE_LIST=/run/smoke-edge-ranges
CDN_SNIPPET=/etc/nginx/snippets/learning-app-cdn-ranges.conf
CDN_DROPIN=/run/systemd/system/learning-app-cdn-ranges.service.d

refresh_cdn_ranges_from() {
    mkdir -p "${CDN_DROPIN}"
    printf '[Service]\nEnvironment=LEARNING_APP__CDN_RANGES_URLS=%s\n' "$1" \
        > "${CDN_DROPIN}/smoke.conf"
    systemctl daemon-reload
    # restart, not start: postinst's own --no-block fetch may still be running,
    # and a start would only wait for it.
    systemctl restart learning-app-cdn-ranges.service
}

# Worker pids do not tell: old workers linger after an earlier reload while
# their connections drain. systemd logs every reload it runs.
nginx_reloads() { journalctl -u nginx.service --no-pager | grep -c 'Reloaded'; }

# Before the first fetch there is no snippet, and nginx must start without it.
rm -f "${CDN_SNIPPET}"
check "cdn ranges: nginx accepts its config before the first fetch" nginx -t

printf '127.0.0.2/32\n' > "${EDGE_LIST}"
reloads_before="$(nginx_reloads)"
check "cdn ranges: the refresh writes the published list" \
    refresh_cdn_ranges_from "file://${EDGE_LIST}"
check "cdn ranges: the snippet trusts the listed range" \
    grep -qx 'set_real_ip_from 127.0.0.2/32;' "${CDN_SNIPPET}"

reloads="$(nginx_reloads)"
check "cdn ranges: a changed list reloads nginx" test "${reloads}" -gt "${reloads_before}"
check "cdn ranges: an unchanged list refreshes clean" \
    refresh_cdn_ranges_from "file://${EDGE_LIST}"
check "cdn ranges: an unchanged list does not reload nginx" \
    test "$(nginx_reloads)" = "${reloads}"

refresh_fails_from() { ! refresh_cdn_ranges_from "$1"; }
printf '127.0.0.3/32\nallow all;\n' > /run/smoke-edge-ranges-garbled
check "cdn ranges: a list with a line that is not a range fails" \
    refresh_fails_from file:///run/smoke-edge-ranges-garbled
check "cdn ranges: a list that cannot be fetched fails" \
    refresh_fails_from file:///run/smoke-edge-ranges-missing
last_good_list_kept() {
    grep -qx 'set_real_ip_from 127.0.0.2/32;' "${CDN_SNIPPET}" &&
    ! grep -q '127\.0\.0\.3' "${CDN_SNIPPET}"
}
check "cdn ranges: failures keep the last good list" last_good_list_kept
check "cdn ranges: failures do not reload nginx" \
    test "$(nginx_reloads)" = "${reloads}"
# A good run clears the unit's failed state for what comes after.
refresh_cdn_ranges_from "file://${EDGE_LIST}" >/dev/null 2>&1

invite() {
    local path="$1"
    shift
    http_code --resolve sprecha.de:443:127.0.0.1 "$@" "https://sprecha.de/api/invite-requests/${path}"
}

# The invite zone lets 10 requests through at once (burst) before the rate.
first_client_exhausts_its_budget() {
    local code
    for _ in $(seq 1 15); do
        code="$(invite smoke-edge-a --interface 127.0.0.2 -H 'CF-Connecting-IP: 198.51.100.1')"
    done
    test "${code}" = 429
}
check "cdn: a client through the edge exhausts its own budget" \
    first_client_exhausts_its_budget
check "cdn: a second client through the same edge has a budget of its own" \
    test "$(invite smoke-edge-b --interface 127.0.0.2 -H 'CF-Connecting-IP: 198.51.100.2')" != 429
check "cdn: a direct request forging the header is not counted as that client" \
    test "$(invite smoke-forged -H 'CF-Connecting-IP: 198.51.100.1')" != 429
# combined logs $remote_addr first: the address every limit and X-Real-IP use.
check "cdn: the edge's client is the request's address" \
    grep -q '^198\.51\.100\.2 .*/api/invite-requests/smoke-edge-b' /var/log/nginx/access.log
check "cdn: a forged header keeps the socket's address" \
    grep -q '^127\.0\.0\.1 .*/api/invite-requests/smoke-forged' /var/log/nginx/access.log

rm -rf "${CDN_DROPIN}"
systemctl daemon-reload

# The same boundary read off the installed config, so a new location that
# forgets the snippet fails here even though the assertion above only exercises
# one of them. `nginx -T` dumps each file separately instead of inlining
# includes, so the snippet is checked on its own and a location counts as safe
# when it includes it or assigns all three headers from auth_request.
STRIP_SNIPPET=/etc/nginx/snippets/couchdb-proxy-auth-strip.conf

strip_snippet_clears_all_three() {
    grep -Eq 'proxy_set_header +X-Auth-CouchDB-UserName +""' "${STRIP_SNIPPET}" &&
    grep -Eq 'proxy_set_header +X-Auth-CouchDB-Roles +""' "${STRIP_SNIPPET}" &&
    grep -Eq 'proxy_set_header +X-Auth-CouchDB-Token +""' "${STRIP_SNIPPET}"
}
check "nginx: the strip snippet clears all three headers" \
    strip_snippet_clears_all_three

every_proxy_location_handles_couch_headers() {
    nginx -T 2>/dev/null | awk '
        /^[[:space:]]*location[^{]*\{/ { inloc = 1; depth = 1; buf = ""; next }
        inloc {
            opens = gsub(/\{/, "{"); closes = gsub(/\}/, "}")
            depth += opens - closes
            buf = buf $0 "\n"
            if (depth <= 0) {
                inloc = 0
                if (buf ~ /proxy_pass/) {
                    assigned = (buf ~ /X-Auth-CouchDB-UserName/) \
                             + (buf ~ /X-Auth-CouchDB-Roles/) \
                             + (buf ~ /X-Auth-CouchDB-Token/)
                    included = (buf ~ /include +snippets\/couchdb-proxy-auth-strip\.conf/)
                    if (assigned != 3 && !included) bad++
                }
            }
        }
        END { exit(bad > 0) }
    '
}
check "nginx: every proxying location handles the proxy-auth headers" \
    every_proxy_location_handles_couch_headers

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
