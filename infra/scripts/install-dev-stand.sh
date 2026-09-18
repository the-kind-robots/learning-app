#!/usr/bin/env bash
# Bring the development stand up on this machine: check every piece the stand
# needs, install what this repository carries, and name what it cannot do.
#
# The stand is nginx in front of the backend (8083), the shadow-cljs watch
# (9630) and CouchDB (5984), with the watch and the backend supervised as
# systemd *user* units. Assembling that by hand meant following three
# documents, and the list drifted: installing the vhost without the snippet it
# includes leaves `nginx -t` failing on a missing file. This script is the one
# copy of the list; the documents point here.
#
# It is safe to re-run. Every step first reports what it found:
#
#     ok       already correct, nothing done
#     todo     missing or different; what would change is printed first
#     done     this run changed it
#     skip     declined
#     manual   the script cannot do it (a credential, a login, another host)
#
# A step that would replace a file prints the difference before asking. Steps
# needing root say so and show the exact command; the answer is asked for, and
# `sudo` never runs without one. --yes answers yes to all of them; answering
# no to every question is the dry run — every step still reports what it found
# and what it would do, and nothing changes.
#
# Exit status: 0 when nothing is left to do, 3 when steps remain undone, 1 on
# an error, 2 on a usage error. The `manual` items are named, never counted as
# undone — no run of this script can clear them.
#
# Usage: install-dev-stand.sh [--yes]
set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: infra/scripts/install-dev-stand.sh [--yes]

  --yes       Do not ask; answer yes to every change, including the root ones.
  --help      This text.

Answering no to every question changes nothing and still reports what is
missing.

Environment (CouchDB admin, dev defaults shown):
  COUCHDB_URL   http://localhost:5984
  COUCHDB_USER  admin
  COUCHDB_PASS  3434
USAGE
}

assume_yes=0

while [ "$#" -gt 0 ]; do
    case "$1" in
        --yes|-y)  assume_yes=1 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "${script_dir}/../.." && pwd)"

# The stand runs as your user: `systemctl --user`, the linger flag and
# node_modules all belong to it. Running the whole script as root would enable
# the units for root and leave a root-owned node_modules behind.
if [ "$(id -u)" -eq 0 ]; then
    echo "run this as your own user, not as root — it asks for root per step" >&2
    exit 2
fi

couchdb_url="${COUCHDB_URL:-http://localhost:5984}"
couchdb_user="${COUCHDB_USER:-admin}"
couchdb_pass="${COUCHDB_PASS:-3434}"

satisfied=0
changed=0
undone=0
named=0

# Set by a step that changed something on this machine, cleared by whoever is
# about to read it. Only two readers: nginx's reload, which costs a root
# prompt and so is not run on a pass that changed nothing, and the note that
# CouchDB has to be restarted.
system_changed=0

section() { printf '\n== %s\n' "$1"; }
ok()      { printf '  ok      %s\n' "$1"; satisfied=$((satisfied + 1)); }
done_()   { printf '  done    %s\n' "$1"; changed=$((changed + 1)); }
skip()    { printf '  skip    %s\n' "$1"; undone=$((undone + 1)); }
todo()    { printf '  todo    %s\n' "$1"; }
manual()  { printf '  manual  %s\n      -> %s\n' "$1" "$2"; named=$((named + 1)); }
note()    { printf '          %s\n' "$1"; }

# Ask, unless --yes. Reads the answer from the terminal rather than stdin, so
# the script still asks when it was itself piped in. No terminal and no --yes
# is a no: an unattended run changes nothing it was not told to change.
confirm() {
    local prompt="$1" answer
    if [ "${assume_yes}" -eq 1 ]; then
        return 0
    fi
    # Opening the terminal can fail even where /dev/tty exists — a detached
    # process has none. That is a no, not an error to print.
    if ! { printf '          %s [y/N] ' "${prompt}" > /dev/tty \
        && read -r answer < /dev/tty; } 2>/dev/null
    then
        return 1
    fi
    case "${answer}" in
        [yY]|[yY][eE][sS]) return 0 ;;
        *) return 1 ;;
    esac
}

# Print what installing this file would change, so the answer is an informed
# one. A file that is not there at all needs no diff.
report_difference() {
    local source="$1" target="$2"
    if [ ! -e "${target}" ]; then
        note "${target} does not exist"
        return 0
    fi
    note "${target} differs from ${source#"${repo_root}"/}:"
    diff -u "${target}" "${source}" | sed -n '3,25p' | sed 's/^/            /' || true
}

# A root step: show the command, ask, then run it under sudo. Never sudo
# without an answer, and never assume the caller already is root.
run_root() {
    local description="$1"
    shift
    note "needs root: sudo $*"
    if confirm "run it?"; then
        sudo "$@"
        done_ "${description}"
        system_changed=1
    else
        skip "${description}"
    fi
}

# Install one repository file to a system path, if it is missing or different.
# -D so a directory the machine does not have yet is created rather than
# failing the step.
install_system_file() {
    local source="$1" target="$2" description="$3"
    if [ -e "${target}" ] && cmp -s "${source}" "${target}"; then
        ok "${description}"
        return 0
    fi
    todo "${description}"
    report_difference "${source}" "${target}"
    run_root "${description}" install -D -m 644 "${source}" "${target}"
}

curl_couch() {
    curl -fsS -m 10 --user "${couchdb_user}:${couchdb_pass}" "$@"
}


section "Toolchain (this script installs none of these)"

# node comes from a version manager that only exists inside a login shell —
# the same reason the watch unit starts through one.
node_shell() { bash -lc "$1"; }

# Only the tools whose absence would end the run in a bare shell error rather
# than in a named step: systemctl runs unconditionally below, and npm runs
# inside the node_modules step. The rest — java, clojure, nginx, loginctl,
# curl, diff, install — are first used inside a step that already reports what
# it found, with more context than a sweep could give.
if command -v systemctl > /dev/null 2>&1; then
    ok "systemctl"
else
    manual "systemctl is not on PATH — the stand is systemd user units" \
        "docs/dev/development-setup.md, Prerequisites"
fi

if command -v npm > /dev/null 2>&1 || node_shell 'command -v npm' > /dev/null 2>&1; then
    ok "npm"
else
    manual "npm is not on PATH, in this shell or a login one" \
        "docs/dev/development-setup.md, Prerequisites"
fi


section "Repository"

if [ -d "${repo_root}/node_modules" ]; then
    ok "node_modules present"
else
    todo "node dependencies are not installed"
    note "would run: npm install"
    if confirm "run it?"; then
        (cd "${repo_root}" && { npm install || node_shell "cd '${repo_root}' && npm install"; })
        done_ "npm install"
    else
        skip "npm install"
    fi
fi

skills_linked=1
for link in "${repo_root}/.claude/skills" "${repo_root}/.codex/skills"; do
    if [ -L "${link}" ] && [ "$(readlink "${link}")" = "../.skills" ]; then
        continue
    fi
    skills_linked=0
done

if [ "${skills_linked}" -eq 1 ]; then
    ok "skills links (.claude/skills, .codex/skills)"
else
    todo "skills links are missing or point elsewhere"
    note "would run: .skills/install.sh --force"
    if confirm "run it?"; then
        "${repo_root}/.skills/install.sh" --force
        done_ "skills links"
    else
        skip "skills links"
    fi
fi


section "nginx"

nginx_source="${repo_root}/infra/development/etc/nginx"
system_changed=0

# The snippet goes first on purpose: the vhost includes it, and nginx refuses
# to start when an included file is missing.
install_system_file \
    "${nginx_source}/snippets/couchdb-proxy-auth-strip.conf" \
    /etc/nginx/snippets/couchdb-proxy-auth-strip.conf \
    "nginx snippet couchdb-proxy-auth-strip.conf"

install_system_file \
    "${nginx_source}/sites-available/sprecha.localhost.conf" \
    /etc/nginx/sites-available/sprecha.localhost.conf \
    "nginx vhost sprecha.localhost.conf"

if [ -L /etc/nginx/sites-enabled/sprecha.localhost.conf ]; then
    ok "nginx vhost enabled"
else
    todo "nginx vhost is not enabled"
    run_root "nginx vhost enabled" \
        ln -sfn /etc/nginx/sites-available/sprecha.localhost.conf \
                /etc/nginx/sites-enabled/sprecha.localhost.conf
fi

if [ "${system_changed}" -eq 1 ]; then
    note "nginx configuration changed; it has to parse before it is reloaded"
    run_root "nginx reloaded" sh -c 'nginx -t && systemctl reload nginx' || true
else
    ok "nginx configuration unchanged, no reload needed"
fi


section "CouchDB"

if curl -fsS -m 10 "${couchdb_url}/" > /dev/null 2>&1; then
    ok "CouchDB answers at ${couchdb_url}"

    system_changed=0
    install_system_file \
        "${repo_root}/infra/development/opt/couchdb/etc/local.d/00-proxy-auth.ini" \
        /opt/couchdb/etc/local.d/00-proxy-auth.ini \
        "CouchDB proxy-auth configuration"
    if [ "${system_changed}" -eq 1 ]; then
        note "CouchDB reads local.d at start — restart it for this to take effect"
    fi

    if curl_couch -o /dev/null "${couchdb_url}/_all_dbs" 2>/dev/null; then
        for database in dictionary-db _global_changes; do
            if curl_couch -o /dev/null "${couchdb_url}/${database}" 2>/dev/null; then
                ok "database ${database}"
            else
                todo "database ${database} does not exist"
                note "would run: curl -X PUT ${couchdb_url}/${database} (as ${couchdb_user})"
                if confirm "create it?"; then
                    curl_couch -X PUT -o /dev/null "${couchdb_url}/${database}"
                    done_ "database ${database}"
                else
                    skip "database ${database}"
                fi
            fi
        done

        # The dictionary is read by anyone and written by the admin alone —
        # an empty members list is what makes it public, as in production.
        if curl_couch "${couchdb_url}/dictionary-db/_security" 2>/dev/null \
            | grep -q '"members":{"names":\[\]'
        then
            ok "dictionary-db is readable without a login"
        else
            todo "dictionary-db security is not the public-read shape"
            if confirm "set it?"; then
                curl_couch -X PUT -o /dev/null \
                    -H 'Content-Type: application/json' \
                    -d '{"admins":{"names":["'"${couchdb_user}"'"],"roles":[]},"members":{"names":[],"roles":[]}}' \
                    "${couchdb_url}/dictionary-db/_security"
                done_ "dictionary-db security"
            else
                skip "dictionary-db security"
            fi
        fi

        if curl_couch "${couchdb_url}/dictionary-db" 2>/dev/null \
            | grep -q '"doc_count":0[,}]'
        then
            todo "dictionary-db is empty — the dictionary is not imported"
            note "would run: clojure -X:dictionary-import  (minutes, ~1M documents)"
            if confirm "import it now?"; then
                (cd "${repo_root}" \
                    && COUCHDB_URL="${couchdb_url}" COUCHDB_PASS="${couchdb_pass}" \
                       clojure -X:dictionary-import)
                done_ "dictionary imported"
            else
                skip "dictionary import"
            fi
        else
            ok "dictionary-db holds documents"
        fi
    else
        manual "CouchDB refused ${couchdb_user}'s credentials — no admin yet, or COUCHDB_PASS is wrong" \
            "docs/dev/development-setup.md, CouchDB Admin Setup"
    fi
else
    manual "CouchDB does not answer at ${couchdb_url}" \
        "docs/dev/development-setup.md, Installing CouchDB"
fi


section "The stand (systemd user units)"

unit_source="${repo_root}/infra/development/etc/systemd/user"

for unit in learning-app-dev-watch.service learning-app-dev-backend.service; do
    install_system_file "${unit_source}/${unit}" "/etc/systemd/user/${unit}" "unit ${unit}"
done

# Milliseconds, needs no root and says nothing when nothing changed, so it
# runs on every pass rather than being tracked into one.
systemctl --user daemon-reload

for unit in learning-app-dev-watch learning-app-dev-backend; do
    if [ "$(systemctl --user is-enabled "${unit}" 2>/dev/null || true)" = "enabled" ] \
        && [ "$(systemctl --user is-active "${unit}" 2>/dev/null || true)" = "active" ]
    then
        ok "${unit} enabled and running"
    else
        todo "${unit} is not both enabled and running"
        note "would run: systemctl --user enable --now ${unit}"
        if confirm "run it?"; then
            systemctl --user enable --now "${unit}"
            done_ "${unit} enabled and running"
        else
            skip "${unit}"
        fi
    fi
done

# Without lingering the user manager stops at logout and takes the stand with
# it, so the units would die with the session they were started from.
if [ "$(loginctl show-user "${USER}" --property=Linger --value 2>/dev/null || true)" = "yes" ]; then
    ok "lingering enabled for ${USER}"
else
    todo "lingering is not enabled — the stand would stop at logout"
    note "would run: loginctl enable-linger ${USER}"
    if confirm "run it?"; then
        loginctl enable-linger "${USER}"
        done_ "lingering enabled"
    else
        skip "lingering"
    fi
fi


section "Named, not this script's to do"

# Each of these is a credential or a login that belongs to a person and to a
# machine, and none of them is carried by this repository.
manual "Cloudflare tunnel token, for <name>.dev.sprecha.de" \
    "docs/dev/mobile-pwa-testing.md, One-time setup"
manual "Tailscale login and 'tailscale serve', for the tailnet name" \
    "docs/dev/mobile-pwa-testing.md, Over the tailnet"

if command -v getent > /dev/null 2>&1 \
    && ! getent hosts sprecha.localhost > /dev/null 2>&1
then
    manual "optional: no /etc/hosts entry for sprecha.localhost — browsers need none, curl may" \
        "docs/dev/development-setup.md, Hosts entry for CLI tools"
fi


printf '\n== Summary\n'
printf '  %d already correct, %d changed, %d left, %d named for you\n' \
    "${satisfied}" "${changed}" "${undone}" "${named}"

if [ "${undone}" -gt 0 ]; then
    printf '  the stand answers at http://sprecha.localhost/ once the rest is done\n'
    exit 3
fi

printf '  the stand answers at http://sprecha.localhost/\n'
