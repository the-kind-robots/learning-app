#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: learning-app-admin-setup [options]

Options:
  --bootstrap            Install base packages and CouchDB repo
  --issue-cert           Issue TLS certificate if missing
  --rotate-openrouter    Re-prompt and overwrite example-generation credential
  --rotate-couchdb       Re-prompt and overwrite CouchDB admin credential
  --rotate-borg          Re-prompt and overwrite Borg passphrase
  --deployer-key <key>   Provide deployer SSH public key
  --borg-repo <repo>     Set or replace BORG_REPO
  -h, --help             Show this help

Examples:
  # First-time setup (packages + cert issuance)
  learning-app-admin-setup --bootstrap --issue-cert

  # Re-run without touching certs
  learning-app-admin-setup

  # Rotate example-generation credential
  learning-app-admin-setup --rotate-openrouter
EOF
}

issue_cert=false
bootstrap=false
rotate_openrouter=false
rotate_couchdb=false
rotate_borg=false
deployer_key=""
borg_repo=""
openrouter_changed=false
couchdb_changed=false
borg_changed=false
borg_repo_changed=false
cert_issued=false
deployer_key_added=false
app_was_active=false
nginx_was_active=false
couchdb_old_password=""
couchdb_new_password=""
couchdb_pending_write=false

while [ $# -gt 0 ]; do
  case "$1" in
    --issue-cert)
      issue_cert=true
      ;;
    --bootstrap)
      bootstrap=true
      ;;
    --rotate-openrouter)
      rotate_openrouter=true
      ;;
    --rotate-couchdb)
      rotate_couchdb=true
      ;;
    --rotate-borg)
      rotate_borg=true
      ;;
    --deployer-key)
      shift
      deployer_key="${1:-}"
      ;;
    --borg-repo)
      shift
      borg_repo="${1:-}"
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "ERROR: Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

if [ "${EUID}" -ne 0 ]; then
  echo "ERROR: Run as root" >&2
  exit 1
fi

info() {
  echo "INFO: $*" >&2
}

warn() {
  echo "WARN: $*" >&2
}

CRED_DIR="/etc/credstore.encrypted"
install -d -m 700 "${CRED_DIR}"

if systemctl is-active --quiet learning-app-run.service; then
  app_was_active=true
fi

if systemctl is-active --quiet nginx; then
  nginx_was_active=true
fi

bootstrap_packages() {
  info "Installing base packages and CouchDB repo"
  apt update
  apt install -y software-properties-common curl apt-transport-https gnupg
  add-apt-repository -y universe

  curl https://couchdb.apache.org/repo/keys.asc | gpg --dearmor | tee /usr/share/keyrings/couchdb-archive-keyring.gpg >/dev/null 2>&1
  . /etc/os-release
  echo "deb [signed-by=/usr/share/keyrings/couchdb-archive-keyring.gpg] https://apache.jfrog.io/artifactory/couchdb-deb/ ${VERSION_CODENAME} main" \
    | tee /etc/apt/sources.list.d/couchdb.list >/dev/null
  apt update

  apt install -y nginx certbot python3-certbot-nginx couchdb borgbackup openjdk-21-jre-headless sqlite3
  apt-get -y -f install
  dpkg --configure -a
}

if [ "${bootstrap}" = true ]; then
  bootstrap_packages
fi

write_credential() {
  local name="$1"
  local prompt="$2"
  local rotate="$3"
  local path="${CRED_DIR}/${name}"

  if [ "${rotate}" = true ] || [ ! -f "${path}" ]; then
    systemd-ask-password -n "${prompt}" \
      | systemd-creds --name="${name}" encrypt - "${path}"
    chmod 600 "${path}"
    case "${name}" in
      openrouter_api_key)
        openrouter_changed=true
        ;;
      couchdb_admin_password)
        couchdb_changed=true
        ;;
      borg-passphrase)
        borg_changed=true
        ;;
    esac
  else
    info "Credential ${name} exists; skipping"
  fi
}

store_credential() {
  local name="$1"
  local value="$2"
  local path="${CRED_DIR}/${name}"

  printf '%s' "${value}" | systemd-creds --name="${name}" encrypt - "${path}"
  chmod 600 "${path}"
  case "${name}" in
    openrouter_api_key)
      openrouter_changed=true
      ;;
    couchdb_admin_password)
      couchdb_changed=true
      ;;
    borg-passphrase)
      borg_changed=true
      ;;
  esac
}

write_credential "openrouter_api_key" "Enter OpenRouter API key for example generation" "${rotate_openrouter}"
if [ "${rotate_couchdb}" = true ]; then
  if [ -f "${CRED_DIR}/couchdb_admin_password" ]; then
    couchdb_old_password=$(systemd-creds --name=couchdb_admin_password decrypt "${CRED_DIR}/couchdb_admin_password" -)
  fi
  couchdb_new_password=$(systemd-ask-password -n "Enter new admin password for CouchDB")
  couchdb_pending_write=true
else
  write_credential "couchdb_admin_password" "Enter admin password for CouchDB" "${rotate_couchdb}"
fi
write_credential "borg-passphrase" "Enter encryption password for BorgBackup" "${rotate_borg}"

# Signs the proxy-auth headers the app hands to CouchDB. Nobody types this one,
# so it is generated rather than asked for; the app refuses to start without it.
if [ ! -f "${CRED_DIR}/db_auth_secret" ]; then
  store_credential "db_auth_secret" "$(openssl rand -hex 32)"
  info "Generated db_auth_secret"
fi

if ! id -u deployer >/dev/null 2>&1; then
  adduser --disabled-password --gecos "" deployer
fi

install -d -m 700 /home/deployer/.ssh
touch /home/deployer/.ssh/authorized_keys
chmod 600 /home/deployer/.ssh/authorized_keys
chown -R deployer:deployer /home/deployer/.ssh

deployer_key="${deployer_key:-${DEPLOY_KEY:-}}"
if [ -z "${deployer_key}" ]; then
  read -r -p "Enter deployer SSH public key (blank to skip): " deployer_key
fi

if [ -n "${deployer_key}" ]; then
  if ! grep -qx "${deployer_key}" /home/deployer/.ssh/authorized_keys; then
    printf '%s\n' "${deployer_key}" >> /home/deployer/.ssh/authorized_keys
    chown deployer:deployer /home/deployer/.ssh/authorized_keys
    deployer_key_added=true
  else
    info "Deployer key already present; skipping"
  fi
else
  warn "No deployer key provided; skipping"
fi

ENV_DIR="/etc/learning-app"
ENV_FILE="${ENV_DIR}/environment"
install -d -m 700 "${ENV_DIR}"
touch "${ENV_FILE}"
chmod 600 "${ENV_FILE}"

set_borg_repo() {
  local repo="$1"
  local current=""
  if grep -q '^BORG_REPO=' "${ENV_FILE}"; then
    current=$(grep -E '^BORG_REPO=' "${ENV_FILE}" | tail -n1 | sed 's/^BORG_REPO=//')
    if [ "${current}" = "${repo}" ]; then
      info "BORG_REPO already set; skipping"
      return
    fi
    sed -i "s#^BORG_REPO=.*#BORG_REPO=${repo}#" "${ENV_FILE}"
  else
    printf '%s\n' "BORG_REPO=${repo}" >> "${ENV_FILE}"
  fi
  borg_repo_changed=true
}

if [ -n "${borg_repo}" ]; then
  set_borg_repo "${borg_repo}"
elif grep -q '^BORG_REPO=' "${ENV_FILE}"; then
  info "BORG_REPO already set; skipping"
else
  read -r -p "Enter BORG_REPO (blank to skip): " borg_repo
  if [ -n "${borg_repo}" ]; then
    set_borg_repo "${borg_repo}"
  else
    warn "BORG_REPO not set; backups will not be enabled"
  fi
fi

if [ "${issue_cert}" = true ]; then
  certbot --nginx --cert-name sprecha.de -d sprecha.de -d www.sprecha.de
  cert_issued=true
else
  info "Skipping certificate issuance (use --issue-cert)"
fi

systemctl enable --now couchdb.service
for i in $(seq 1 30); do
  curl -sf http://127.0.0.1:5984/ >/dev/null && break
  sleep 1
done

if ! curl -sf http://127.0.0.1:5984/ >/dev/null 2>&1; then
  echo "ERROR: CouchDB did not start within 30 seconds" >&2
  exit 1
fi

if [ -f "${CRED_DIR}/couchdb_admin_password" ] || [ "${couchdb_pending_write}" = true ]; then
  if [ "${couchdb_pending_write}" = true ]; then
    COUCH_PASS="${couchdb_new_password}"
  else
    COUCH_PASS=$(systemd-creds --name=couchdb_admin_password decrypt "${CRED_DIR}/couchdb_admin_password" -)
  fi

  admin_party=false
  if curl -sf http://127.0.0.1:5984/_node/_local/_config/admins >/dev/null 2>&1; then
    admin_party=true
  fi

  if [ "${admin_party}" = true ]; then
    curl -sf -X PUT http://127.0.0.1:5984/_node/_local/_config/admins/admin \
      -d "\"${COUCH_PASS}\"" >/dev/null
  elif [ "${rotate_couchdb}" = true ]; then
    if [ -z "${couchdb_old_password}" ]; then
      echo "ERROR: CouchDB admin rotation requires existing password in ${CRED_DIR}/couchdb_admin_password" >&2
      exit 1
    fi
    curl -sf -X PUT -u "admin:${couchdb_old_password}" \
      http://127.0.0.1:5984/_node/_local/_config/admins/admin \
      -d "\"${COUCH_PASS}\"" >/dev/null
  fi

  if [ "${couchdb_pending_write}" = true ]; then
    store_credential "couchdb_admin_password" "${couchdb_new_password}"
    couchdb_pending_write=false
  fi

  if ! curl -sf -u "admin:${COUCH_PASS}" http://127.0.0.1:5984/dictionary-db >/dev/null 2>&1; then
    curl -sf -X PUT -u "admin:${COUCH_PASS}" http://127.0.0.1:5984/dictionary-db >/dev/null
  fi

  curl -sf -X PUT -u "admin:${COUCH_PASS}" \
    http://127.0.0.1:5984/dictionary-db/_security \
    -H "Content-Type: application/json" \
    -d '{"admins":{"names":["admin"],"roles":[]},"members":{"names":[],"roles":[]}}' \
    >/dev/null
else
  warn "Missing couchdb_admin_password; skipping CouchDB admin and dictionary-db setup"
fi

systemctl daemon-reload
systemctl enable --now nginx
systemctl enable --now learning-app-restart.path
systemctl enable --now learning-app-certbot.timer

has_openrouter_key=false
if grep -qs '^OPENROUTER_API_KEY=' /etc/learning-app/environment /etc/environment.d/learning-app.conf \
   || [ -f "${CRED_DIR}/openrouter_api_key" ]; then
  has_openrouter_key=true
fi

if [ "${has_openrouter_key}" = true ]; then
  systemctl enable --now learning-app-run.service
else
  warn "Missing OPENROUTER_API_KEY credential; not starting learning-app-run.service"
fi

has_borg_repo=false
if grep -qs '^BORG_REPO=' /etc/learning-app/environment; then
  has_borg_repo=true
fi

if [ -f "${CRED_DIR}/borg-passphrase" ] && [ "${has_borg_repo}" = true ]; then
  systemctl enable --now learning-app-backup-db.timer
else
  warn "Missing borg-passphrase or BORG_REPO; not starting learning-app-backup-db.timer"
fi

if [ "${cert_issued}" = true ] && [ "${nginx_was_active}" = true ]; then
  nginx -t
  systemctl reload nginx
fi

if [ "${openrouter_changed}" = true ]; then
  systemctl enable --now learning-app-run.service
fi
