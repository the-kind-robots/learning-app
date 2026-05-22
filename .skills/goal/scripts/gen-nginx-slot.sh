#!/usr/bin/env bash
# Generate an nginx vhost config for a worktree slot.
# Usage: gen-nginx-slot.sh <slot> <certs-dir>
# Outputs the config to stdout and writes it to /etc/nginx/sites-available/sprecha-slot<N>.conf
# Requires sudo for the write step.

set -euo pipefail

SLOT="${1:?slot number required}"
CERTS_DIR="${2:?certs dir required}"

BACKEND_PORT=$((8083 + SLOT * 100))
NREPL_PORT=$((4444 + SLOT * 100))
SHADOW_PORT=$((9630 + SLOT * 100))
HTTPS_PORT=$((4430 + SLOT))
DOMAIN="sprecha.local"

CONFIG=$(cat <<EOF
# Slot ${SLOT} — backend:${BACKEND_PORT}, shadow:${SHADOW_PORT}
server {
    listen 80;
    server_name ${DOMAIN};
    listen ${HTTPS_PORT};
    return 301 https://\$host:${HTTPS_PORT}\$request_uri;
}

server {
    listen ${HTTPS_PORT} ssl;
    server_name ${DOMAIN};

    ssl_certificate     ${CERTS_DIR}/sprecha.local+3.pem;
    ssl_certificate_key ${CERTS_DIR}/sprecha.local+3-key.pem;

    location /shadow-cljs/ {
        proxy_pass http://localhost:${SHADOW_PORT}/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_read_timeout 1h;
    }

    proxy_set_header X-Auth-CouchDB-UserName "";
    proxy_set_header X-Auth-CouchDB-Roles    "";
    proxy_set_header X-Auth-CouchDB-Token    "";

    location /db/dictionary-db/ {
        proxy_pass http://localhost:5984/dictionary-db/;
        proxy_redirect off;
        proxy_buffering off;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }

    location /db/ {
        auth_request /auth/check;
        auth_request_set \$couch_user  \$upstream_http_x_auth_user;
        auth_request_set \$couch_roles \$upstream_http_x_auth_roles;
        auth_request_set \$couch_token \$upstream_http_x_auth_token;
        proxy_set_header X-Auth-CouchDB-UserName \$couch_user;
        proxy_set_header X-Auth-CouchDB-Roles    \$couch_roles;
        proxy_set_header X-Auth-CouchDB-Token    \$couch_token;
        proxy_pass http://localhost:5984/;
        proxy_redirect off;
        proxy_buffering off;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    }

    location /auth/check {
        internal;
        proxy_pass http://localhost:${BACKEND_PORT}/auth/check;
        proxy_set_header Content-Length "";
        proxy_pass_request_body off;
    }

    location / {
        proxy_pass http://localhost:${BACKEND_PORT};
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
    }
}
EOF
)

echo "$CONFIG"
echo ""
echo "# To activate (requires sudo):"
echo "# sudo tee /etc/nginx/sites-available/sprecha-slot${SLOT}.conf <<'NGINXEOF'"
echo "# $CONFIG"
echo "# NGINXEOF"
echo "# sudo ln -sf /etc/nginx/sites-available/sprecha-slot${SLOT}.conf /etc/nginx/sites-enabled/"
echo "# sudo nginx -s reload"
echo ""
echo "# Preview URL: https://sprecha.local:${HTTPS_PORT}/"
