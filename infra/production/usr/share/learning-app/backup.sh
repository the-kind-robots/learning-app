#!/usr/bin/bash
set -euo pipefail

: "${BORG_REPO:=ssh://storagebox/./borg-repository}"
export BORG_REPO

if [ -z "${BORG_PASSPHRASE_PATH:-}" ] || [ ! -f "${BORG_PASSPHRASE_PATH}" ]; then
    echo "ERROR: BORG_PASSPHRASE_PATH is missing" >&2
    exit 1
fi

BORG_PASSPHRASE=$(cat "${BORG_PASSPHRASE_PATH}")
export BORG_PASSPHRASE

: "${LEARNING_APP__DB_PATH:=/var/lib/learning-app/db.sqlite}"
export LEARNING_APP__DB_PATH
: "${LEARNING_APP__DB_BACKUP_PATH:?LEARNING_APP__DB_BACKUP_PATH is missing}"


# some helpers and error handling:
info() { printf "\n%s %s\n\n" "$( date )" "$*" >&2; }

trap 'echo "$( date ) Backup interrupted" >&2; exit 2' INT TERM


info "Starting backup"

# Local snapshot. Nothing below runs unless the snapshot was taken and passed
# its integrity check: an unverified snapshot never becomes an archive.
if ! sqlite3 "${LEARNING_APP__DB_PATH}" ".backup '${LEARNING_APP__DB_BACKUP_PATH}'"; then
    info "Snapshot failed; no archive created"
    exit 2
fi
info "Check backup"
if ! sqlite3 "${LEARNING_APP__DB_BACKUP_PATH}" ".selftest"; then
    info "Snapshot failed its self-test; no archive created"
    exit 2
fi

# Create remote backup. One archive carries both stores so a restore is
# coherent by construction: an account row and its userdb come from the same
# moment. CouchDB files are append-only, so a live copy is recoverable —
# still, the archive is taken right after the sqlite snapshot to keep the
# two as close as possible.
# borg exits 1 on warnings and 2 on errors. Each code is recorded rather than
# fatal, so prune and compact still run and the worst code is the script's.
borg_backup_exit=0
borg create                     \
    --list                      \
    --stats                     \
    --show-rc                   \
    '::app-{now}'               \
    "${LEARNING_APP__DB_BACKUP_PATH}" \
    /var/lib/couchdb            \
    || borg_backup_exit=$?


info "Pruning repository"

# Use the `prune` subcommand to maintain 7 daily, 4 weekly and 6 monthly archives.

prune_exit=0
borg prune            \
    --list            \
    --show-rc         \
    --keep-daily    7 \
    --keep-weekly   4 \
    --keep-monthly  6 \
    || prune_exit=$?


# actually free repo disk space by compacting segments

info "Compacting repository"

compact_exit=0
borg compact || compact_exit=$?

# use highest exit code as global exit code
global_exit=${borg_backup_exit}
global_exit=$(( prune_exit > global_exit ? prune_exit : global_exit ))
global_exit=$(( compact_exit > global_exit ? compact_exit : global_exit ))

if [ "${global_exit}" -eq 0 ]; then
    info "Backup, Prune, and Compact finished successfully"
elif [ "${global_exit}" -eq 1 ]; then
    info "Backup, Prune, and/or Compact finished with warnings"
else
    info "Backup, Prune, and/or Compact finished with errors"
fi

exit "${global_exit}"
