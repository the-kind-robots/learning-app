#!/usr/bin/env bash
# Keeps the CDN's address ranges current for nginx (GH-801). Run daily by
# learning-app-cdn-ranges.timer. It writes this file, which the nginx site
# includes; nginx believes CF-Connecting-IP only from the ranges in it:
#
#   # Written by learning-app-cdn-ranges.service; do not edit.
#   set_real_ip_from 173.245.48.0/20;
#   set_real_ip_from 103.21.244.0/22;
#   ...
#   set_real_ip_from 2400:cb00::/32;
#   ...
#
# 1. Fetch the CDN's lists: one range per line.
# 2. Refuse them unless every line is a range.
# 3. Nothing changed: stop, no reload.
# 4. Put the new file in place; if `nginx -t` refuses it, put the old one back.
# 5. Reload nginx.
#
# Any failure leaves the file as it was.
set -euo pipefail

SNIPPET="/etc/nginx/snippets/learning-app-cdn-ranges.conf"
SOURCES="${LEARNING_APP__CDN_RANGES_URLS:-https://www.cloudflare.com/ips-v4 https://www.cloudflare.com/ips-v6}"

work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

fail() {
    echo "ERROR: $*; ${SNIPPET} stays as it was" >&2
    exit 1
}

# 1. Fetch. The lists may use \r\n, blank lines, or no newline at the end;
# `awk NF` drops blank lines and ends every line with a newline.
for url in ${SOURCES}; do
    curl -fsS --max-time 30 --retry 2 -o "${work}/list" "${url}" \
        || fail "cannot fetch ${url}"
    tr -d '\r' < "${work}/list" | awk NF > "${work}/list-lines"
    [ -s "${work}/list-lines" ] || fail "${url} lists no range"
    cat "${work}/list-lines" >> "${work}/ranges"
done

# 2. Check. The file is nginx configuration. A range holds only digits, hex
# letters, dots, colons and a slash, so nothing fetched can add a directive.
if grep -qvE '^[0-9a-fA-F.:]+/[0-9]{1,3}$' "${work}/ranges"; then
    fail "a list has a line that is not a range"
fi
{
    echo "# Written by learning-app-cdn-ranges.service; do not edit."
    sed 's/.*/set_real_ip_from &;/' "${work}/ranges"
} > "${work}/snippet"

# 3. Unchanged.
if cmp -s "${work}/snippet" "${SNIPPET}"; then
    echo "CDN ranges unchanged"
    exit 0
fi

# 4. In place. Before the first fetch there is no old file: nginx includes the
# snippet by a mask, so "put it back" means remove the new one.
[ -f "${SNIPPET}" ] && cp "${SNIPPET}" "${work}/previous"
install -m 644 "${work}/snippet" "${SNIPPET}"
if ! nginx -t; then
    if [ -f "${work}/previous" ]; then
        install -m 644 "${work}/previous" "${SNIPPET}"
    else
        rm -f "${SNIPPET}"
    fi
    fail "nginx -t refused the new ranges"
fi

# 5. Reload.
systemctl reload nginx
echo "CDN ranges updated, nginx reloaded"
