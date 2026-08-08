#!/bin/sh
# Exports systemd credentials as the env vars the app reads. Credentials are
# the single source of truth for secrets: a value that leaked into an env
# file is overwritten, so there is exactly one place to rotate. The files
# are guaranteed by LoadCredentialEncrypted= — a missing one already failed
# the unit before this script ran.
set -eu

env_from_file() {
    var="$1"
    file="$2"
    if [ -n "$file" ] && [ -f "$file" ]; then
        export "$var"="$(cat "$file")"
    fi
}

env_from_file OPENROUTER_API_KEY              "${OPENROUTER_API_KEY_PATH:-}"
env_from_file LEARNING_APP_DB_AUTH_SECRET     "${LEARNING_APP_DB_AUTH_SECRET_PATH:-}"
env_from_file LEARNING_APP__COUCHDB_PASSWORD  "${LEARNING_APP__COUCHDB_PASSWORD_PATH:-}"

exec java -jar /opt/learning-app/learning-app.jar
