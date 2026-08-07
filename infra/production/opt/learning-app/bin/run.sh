#!/bin/sh
# Bridges systemd credentials into the environment the app reads: every
# secret is a file under $CREDENTIALS_DIRECTORY (decrypted by systemd),
# and the app wants plain env vars. An already-set variable wins, so a
# dev override in /etc/learning-app/environment keeps working.
set -eu

env_from_file() {
    var="$1"
    file="$2"
    if [ -z "$(eval "printf %s \"\${$var:-}\"")" ] && [ -n "$file" ] && [ -f "$file" ]; then
        export "$var"="$(cat "$file")"
    fi
}

env_from_file OPENROUTER_API_KEY              "${OPENROUTER_API_KEY_PATH:-}"
env_from_file LEARNING_APP_DB_AUTH_SECRET     "${LEARNING_APP_DB_AUTH_SECRET_PATH:-}"
env_from_file LEARNING_APP__COUCHDB_PASSWORD  "${LEARNING_APP__COUCHDB_PASSWORD_PATH:-}"

exec java -jar /opt/learning-app/learning-app.jar
