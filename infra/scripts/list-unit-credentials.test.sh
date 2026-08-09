#!/usr/bin/env bash
# Tests for list-unit-credentials.sh. Run from anywhere; exits non-zero on
# the first failing case.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
subject="${script_dir}/list-unit-credentials.sh"
repo_root="$(cd "${script_dir}/../.." && pwd)"

tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT

fail() {
    echo "FAIL: $1" >&2
    echo "expected:" >&2
    echo "$2" | sed 's/^/  /' >&2
    echo "actual:" >&2
    echo "$3" | sed 's/^/  /' >&2
    exit 1
}

check() {
    local label="$1" expected="$2" actual="$3"
    if [ "${actual}" != "${expected}" ]; then
        fail "${label}" "${expected}" "${actual}"
    fi
    echo "ok: ${label}"
}

# --- name:path form, duplicates across files collapse, output sorted -------

cat > "${tmp}/a.service" <<'EOF'
[Service]
LoadCredentialEncrypted=zeta:/etc/credstore.encrypted/zeta
LoadCredentialEncrypted=alpha:/etc/credstore.encrypted/alpha
EOF

cat > "${tmp}/b.service" <<'EOF'
[Service]
LoadCredentialEncrypted=alpha:/etc/credstore.encrypted/alpha
EOF

check "names deduplicated and sorted" \
    "$(printf 'alpha\nzeta')" \
    "$("${subject}" "${tmp}/a.service" "${tmp}/b.service")"

# --- bare NAME form (no path), spaces around '=' ----------------------------

cat > "${tmp}/c.service" <<'EOF'
[Service]
LoadCredentialEncrypted=bare-name
  LoadCredentialEncrypted = spaced.name : /some/path
EOF

check "bare name and spaced assignment" \
    "$(printf 'bare-name\nspaced.name')" \
    "$("${subject}" "${tmp}/c.service")"

# --- reset directive and lookalikes are ignored ------------------------------

cat > "${tmp}/d.service" <<'EOF'
[Service]
LoadCredentialEncrypted=
LoadCredential=plain:/etc/credstore/plain
# LoadCredentialEncrypted=commented:/etc/credstore.encrypted/commented
Environment=X=LoadCredentialEncrypted=not-a-directive
LoadCredentialEncrypted=real:/etc/credstore.encrypted/real
EOF

check "reset, plain LoadCredential, comments, env lookalike ignored" \
    "real" \
    "$("${subject}" "${tmp}/d.service")"

# --- no directives at all: empty output, exit 0 ------------------------------

cat > "${tmp}/e.timer" <<'EOF'
[Timer]
OnCalendar=daily
EOF

check "unit without credentials yields empty output" \
    "" \
    "$("${subject}" "${tmp}/e.timer")"

# --- no arguments is a usage error -------------------------------------------

if "${subject}" >/dev/null 2>&1; then
    echo "FAIL: no arguments should exit non-zero" >&2
    exit 1
fi
echo "ok: no arguments exits non-zero"

# --- the real production units -----------------------------------------------

check "production units list the expected credentials" \
    "$(printf 'borg-passphrase\ncouchdb_admin_password\ndb_auth_secret\nopenrouter_api_key')" \
    "$("${subject}" "${repo_root}"/infra/production/etc/systemd/system/*)"

echo "all tests passed"
