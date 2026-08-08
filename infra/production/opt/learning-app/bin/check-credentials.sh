#!/usr/bin/env bash
# Deploy preflight helper: verify each named systemd credential file exists
# in /etc/credstore.encrypted. Prints the missing NAMES only — never values,
# never file contents — and exits 1 when anything is absent.
#
# Runs as root via a dedicated sudoers entry (see DEBIAN/postinst): the
# credstore directory is 0700 root, so the unprivileged deploy user cannot
# stat the files itself. Read-only by construction; names are validated so
# the argument list cannot point outside the credstore.
#
# Usage: check-credentials.sh NAME...
set -euo pipefail

CRED_DIR="/etc/credstore.encrypted"

if [ "$#" -eq 0 ]; then
    echo "usage: $0 NAME..." >&2
    exit 2
fi

missing=()
for name in "$@"; do
    case "${name}" in
        '' | *[!A-Za-z0-9._-]*)
            echo "ERROR: invalid credential name: ${name}" >&2
            exit 2
            ;;
    esac
    if [ ! -f "${CRED_DIR}/${name}" ]; then
        missing+=("${name}")
    fi
done

if [ "${#missing[@]}" -gt 0 ]; then
    echo "Missing credentials in ${CRED_DIR}:" >&2
    printf '  %s\n' "${missing[@]}" >&2
    exit 1
fi

echo "All ${#} credential(s) present in ${CRED_DIR}"
