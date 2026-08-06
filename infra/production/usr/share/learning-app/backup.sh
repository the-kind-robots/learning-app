#!/usr/bin/bash

: "${BORG_REPO:=ssh://storagebox/./borg-repository}"
export BORG_REPO

if [ -z "${BORG_PASSPHRASE_PATH:-}" ] || [ ! -f "${BORG_PASSPHRASE_PATH}" ]; then
    echo "ERROR: BORG_PASSPHRASE_PATH is missing" >&2
    exit 1
fi

export BORG_PASSPHRASE=$(cat "${BORG_PASSPHRASE_PATH}")

: "${LEARNING_APP_DB_PATH:=/var/lib/learning-app/db.sqlite}"
export LEARNING_APP_DB_PATH


# some helpers and error handling:
info() { printf "\n%s %s\n\n" "$( date )" "$*" >&2; }

trap 'echo $( date ) Backup interrupted >&2; exit 2' INT TERM


info "Starting backup"

# Create local backup
sqlite3 ${LEARNING_APP_DB_PATH} ".backup $LEARNING_APP_DB_BACKUP_PATH"
info "Check backup"
sqlite3 ${LEARNING_APP_DB_BACKUP_PATH} ".selftest"

sqlite_backup_exit=$?

# Create remote backup. One archive carries both stores so a restore is
# coherent by construction: an account row and its userdb come from the same
# moment. CouchDB files are append-only, so a live copy is recoverable —
# still, the archive is taken right after the sqlite snapshot to keep the
# two as close as possible.
borg create                     \
    --list                      \
    --stats                     \
    --show-rc                   \
    ::app-{now}                 \
    $LEARNING_APP_DB_BACKUP_PATH \
    /var/lib/couchdb

borg_backup_exit=$?


info "Pruning repository"

# Use the `prune` subcommand to maintain 7 daily, 4 weekly and 6 monthly archives.

borg prune            \
    --list            \
    --show-rc         \
    --keep-daily    7 \
    --keep-weekly   4 \
    --keep-monthly  6

prune_exit=$?


# actually free repo disk space by compacting segments

info "Compacting repository"

borg compact

compact_exit=$?

# use highest exit code as global exit code
global_exit=$(( borg_backup_exit > sqlite_backup_exit ? borg_backup_exit : sqlite_backup_exit ))
global_exit=$(( prune_exit > global_exit ? prune_exit : global_exit ))
global_exit=$(( compact_exit > global_exit ? compact_exit : global_exit ))

if [ ${global_exit} -eq 0 ]; then
    info "Backup, Prune, and Compact finished successfully"
elif [ ${global_exit} -eq 1 ]; then
    info "Backup, Prune, and/or Compact finished with warnings"
else
    info "Backup, Prune, and/or Compact finished with errors"
fi

exit ${global_exit}
