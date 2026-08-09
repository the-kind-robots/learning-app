#!/usr/bin/env bash
# Print the credential names the given systemd units load via
# LoadCredentialEncrypted=, one name per line, sorted, deduplicated.
#
# A directive looks like
#     LoadCredentialEncrypted=NAME:PATH
# or, when the credential is searched in the default credstore,
#     LoadCredentialEncrypted=NAME
# An empty value (LoadCredentialEncrypted=) resets the list and names
# nothing, so it is ignored. No referenced credentials is not an error:
# the script prints nothing and exits 0.
#
# Usage: list-unit-credentials.sh UNIT-FILE...
set -euo pipefail

if [ "$#" -eq 0 ]; then
    echo "usage: $0 UNIT-FILE..." >&2
    exit 2
fi

awk '
    /^[[:space:]]*LoadCredentialEncrypted[[:space:]]*=/ {
        sub(/^[[:space:]]*LoadCredentialEncrypted[[:space:]]*=[[:space:]]*/, "")
        split($0, parts, ":")
        gsub(/[[:space:]]+$/, "", parts[1])
        if (parts[1] != "") print parts[1]
    }
' "$@" | sort -u
