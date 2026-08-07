#!/usr/bin/env bash
# Staging rung 2 (ADR-0010): build the deb the way CI does, install it inside
# a systemd container, and smoke the production shape. See README.md for what
# this proves and what it deliberately does not.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${HERE}/../.." && pwd)"

IMAGE="${LEARNING_APP_STAGING_IMAGE:-learning-app-staging}"
CONTAINER="${LEARNING_APP_STAGING_CONTAINER:-learning-app-staging-run}"
KEEP="${LEARNING_APP_STAGING_KEEP:-0}"

# Test-only secrets, plaintext by design: nothing in this container is real.
# CouchDB's password is the app's hardcoded one (lib/db/src/db.cljc `conn`) —
# see the Dockerfile preseed comment; the two must stay identical.
COUCH_PASS="3434"                 # must match the debconf preseed in Dockerfile
BORG_PASS="staging-borg-pass"     # must match smoke.sh

cleanup() {
    if [ "${KEEP}" = 1 ]; then
        echo "KEEP=1 — leaving container ${CONTAINER} running"
    else
        docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

in_c() {
    docker exec "${CONTAINER}" "$@"
}

encrypt_credential() {
    # systemd-creds needs a real machine behind it; inside this container
    # `encrypt --with-key=host` works and generates
    # /var/lib/systemd/credential.secret on first use (no TPM2 involved).
    local name="$1"
    printf '%s' "$2" | docker exec -i "${CONTAINER}" \
        systemd-creds encrypt --name="${name}" --with-key=host \
        - "/etc/credstore.encrypted/${name}"
}

echo "== Build the infra deb (identical to infra-checks.yml) =="
mkdir -p "${ROOT}/target"
dpkg-deb --root-owner-group --build "${ROOT}/infra/production" \
    "${ROOT}/target/learning-app-infra.deb"

if [ ! -f "${ROOT}/target/learning-app.jar" ]; then
    echo "ERROR: target/learning-app.jar missing. Build it the way deploy-app.yml does:" >&2
    echo "  npm ci && npx shadow-cljs release app && clojure -T:build uber" >&2
    exit 1
fi

echo "== Build the container image =="
docker build -t "${IMAGE}" "${HERE}"

echo "== Boot systemd =="
# The incantation this daemon needed (docker 29.2, cgroup v2, WSL2 kernel),
# recorded per ADR-0010 so it is never rediscovered:
# - default private cgroupns leaves /sys/fs/cgroup read-only and PID 1 dies
#   with "Failed to create /init.scope control group" -> host cgroupns plus a
#   rw cgroup bind;
# - the units' production sandboxes (ProtectSystem, PrivateTmp,
#   LoadCredentialEncrypted's credential mount) die with "Failed to keep
#   CAP_SYS_ADMIN" under docker's default caps/seccomp -> CAP_SYS_ADMIN plus
#   an unconfined seccomp/apparmor profile;
# - docker leaves every mount private and systemd-in-a-container skips its
#   usual "remount / rshared" setup, so LoadCredentialEncrypted's staged
#   credential mount is MS_MOVEd into a namespace nobody sees: the service
#   runs but /run/credentials/<unit> is empty. `mount --make-rshared /`
#   after boot (below) restores propagation. --privileged does NOT fix this
#   one and is not needed once these three are in place.
# No ports are published on purpose — every assertion goes through docker exec.
docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
docker run -d --name "${CONTAINER}" \
    --cgroupns=host -v /sys/fs/cgroup:/sys/fs/cgroup:rw \
    --tmpfs /run --tmpfs /run/lock \
    --cap-add=SYS_ADMIN \
    --security-opt seccomp=unconfined --security-opt apparmor=unconfined \
    "${IMAGE}" >/dev/null

state=""
for _ in $(seq 1 60); do
    state="$(docker exec "${CONTAINER}" systemctl is-system-running 2>/dev/null || true)"
    case "${state}" in running|degraded) break ;; esac
    sleep 1
done
case "${state}" in
    running|degraded)
        echo "systemd is up (${state})"
        # See the incantation note above: without this, per-service encrypted
        # credentials silently never arrive.
        in_c mount --make-rshared /
        ;;
    *)
        echo "ERROR: systemd did not come up (state: ${state:-none})" >&2
        docker logs "${CONTAINER}" 2>&1 | tail -20 >&2
        exit 1
        ;;
esac

echo "== Stage inputs =="
docker cp "${ROOT}/target/learning-app-infra.deb" "${CONTAINER}:/root/"
docker cp "${ROOT}/target/learning-app.jar" "${CONTAINER}:/root/"
docker cp "${HERE}/smoke.sh" "${CONTAINER}:/root/smoke.sh"
docker cp "${HERE}/restore-drill.sh" "${CONTAINER}:/root/restore-drill.sh"

echo "== Credentials the operator creates via admin-setup.sh =="
# The OpenRouter key is withheld until after the first install so postinst's
# gate ("no key -> do not start the app") is exercised for real.
in_c install -d -m 700 /etc/credstore.encrypted
encrypt_credential couchdb_admin_password "${COUCH_PASS}"
encrypt_credential db_auth_secret "$(in_c openssl rand -hex 32)"
encrypt_credential borg-passphrase "${BORG_PASS}"
in_c bash -c 'install -d -m 700 /etc/learning-app
              printf "BORG_REPO=/var/lib/dbmaintainer/borg-repo\n" > /etc/learning-app/environment
              chmod 600 /etc/learning-app/environment'

echo "== Pre-place the legacy database (#209 adoption scenario) =="
# Before ANY service start: the pre-#201 world where the data lives at
# /opt/learning-app/app.db and /var/lib/learning-app/db.sqlite does not hold it.
docker exec -i "${CONTAINER}" bash -c 'mkdir -p /opt/learning-app && sqlite3 /opt/learning-app/app.db' <<'SQL'
CREATE TABLE staging_marker(note TEXT);
INSERT INTO staging_marker VALUES ('legacy');
SQL

echo "== Install the deb (runbook step 1) =="
in_c bash -c 'dpkg -i /root/learning-app-infra.deb || apt-get -f install -y'

if in_c systemctl is-active --quiet learning-app-run.service; then
    echo "ERROR: learning-app-run started without an OpenRouter key — postinst gate broken" >&2
    exit 1
fi
echo "postinst gate held: learning-app-run not started without OPENROUTER key"

echo "== Complete the operator setup: perms, key, jar (runbook step 2) =="
# /opt/learning-app mirrors the dictionary dir pattern: deployer writes the
# jar, webapp (group) needs write to retire the legacy db after adoption.
in_c bash -c 'chown deployer:webapp /opt/learning-app
              chmod 2775 /opt/learning-app
              chown webapp:webapp /opt/learning-app/app.db'
encrypt_credential openrouter_api_key "staging-dummy-openrouter-key"
# Atomic replace, exactly like the runbook — this fires learning-app-restart.path.
in_c bash -c 'cp /root/learning-app.jar /opt/learning-app/learning-app.jar.tmp
              mv -f /opt/learning-app/learning-app.jar.tmp /opt/learning-app/learning-app.jar'
# Reinstall is idempotent per the runbook; with the key present postinst now
# enables learning-app-run.
in_c bash -c 'dpkg -i /root/learning-app-infra.deb'

echo "== Wait for the app =="
up=0
for _ in $(seq 1 90); do
    if in_c curl -sf -o /dev/null http://127.0.0.1:8083/; then up=1; break; fi
    sleep 1
done
if [ "${up}" != 1 ]; then
    echo "ERROR: app did not answer on 127.0.0.1:8083 within 90s" >&2
    in_c systemctl status learning-app-run.service --no-pager >&2 || true
    in_c journalctl -u learning-app-run.service --no-pager 2>&1 | tail -30 >&2 || true
    exit 1
fi

echo "== Local borg repository (remote borg is out of scope here) =="
in_c runuser -u dbmaintainer -- env \
    BORG_REPO=/var/lib/dbmaintainer/borg-repo \
    BORG_PASSPHRASE="${BORG_PASS}" \
    borg init --encryption=repokey

echo "== Smoke =="
smoke_exit=0
in_c bash /root/smoke.sh || smoke_exit=$?

# The drill runs even on a red smoke: its subject (backup/restore) is
# independent of whichever smoke assertion is failing that day.
echo "== Restore drill (GH-191) =="
drill_exit=0
in_c bash /root/restore-drill.sh || drill_exit=$?

exit $(( smoke_exit > drill_exit ? smoke_exit : drill_exit ))
