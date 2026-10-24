#!/usr/bin/env bash
# Read notes captured in Google Tasks and close them once they are tracked work.
#
# Credentials and account-specific settings live outside the repository, under
# ${XDG_CONFIG_HOME:-$HOME/.config}/learning-app/. Nothing secret is printed:
# `doctor` reports that a token works, never what it is, and `auth` redacts the
# token oauth2l writes to stdout.
#
# Sourcing this file defines its functions without running anything, which is how
# the output shaping is exercised against saved fixtures.

set -euo pipefail

SKILL_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
SETUP_DOC="$SKILL_DIR/references/setup.md"

API_ROOT="https://tasks.googleapis.com/tasks/v1"
SCOPE="https://www.googleapis.com/auth/tasks"

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/learning-app"
CONFIG_FILE="$CONFIG_DIR/google-tasks.env"

AUTH_HEADER=""

usage() {
  cat <<USAGE
Usage: google_tasks.sh <command> [options]

Pull notes captured in Google Tasks into the repository's delivery flow.

Commands:
  doctor            Report whether the credentials and the default list are usable
  auth              Run the one-time browser consent
  lists             Print the account's task lists (id and title)
  pull              Print the open tasks of a list (id, title, note)
  done <task-id>    Mark a task completed, so the note is not read twice

Options:
  -h, --help        Show this help, or the help of a command

Configuration ($CONFIG_FILE, overridden by the environment):
  GOOGLE_TASKS_CLIENT_JSON   OAuth client JSON
                             (default: $CONFIG_DIR/google-tasks-client.json)
  GOOGLE_TASKS_LIST          Default task list, by title or id (default: @default)

One-time setup: $SETUP_DOC
USAGE
}

usage_doctor() {
  cat <<'USAGE'
Usage: google_tasks.sh doctor

Report, one line per check: oauth2l, jq and curl on PATH, the config file, the
OAuth client JSON, a token refresh, and whether the default list resolves.

Prints no secret. Exits non-zero when any check fails, naming what is missing.
USAGE
}

usage_auth() {
  cat <<'USAGE'
Usage: google_tasks.sh auth

Run the one-time browser consent and cache the refresh token in ~/.oauth2l.

Auto-opening a browser is unreliable under WSL2, so the consent URL is printed
for you to paste yourself. The access token oauth2l prints is redacted here.

oauth2l exits 0 even when consent never completes, so this verifies the result
with a token refresh afterwards and reports that instead.
USAGE
}

usage_lists() {
  cat <<'USAGE'
Usage: google_tasks.sh lists [--json]

Print the account's task lists as "<id>  <title>".

Options:
  --json    Print the API's list array unchanged
USAGE
}

usage_pull() {
  cat <<'USAGE'
Usage: google_tasks.sh pull [--list <title-or-id>] [--max <n>] [--json]

Print the OPEN tasks of a list: title, id, due date, and the note body.
Completed and hidden tasks are never returned, so a note that has already
become tracked work does not come back.

Options:
  --list <title-or-id>   List to read (default: GOOGLE_TASKS_LIST, or @default)
  --max <n>              Maximum tasks to fetch, 1-100 (default: 100)
  --json                 Print the API's task array unchanged
USAGE
}

usage_done() {
  cat <<'USAGE'
Usage: google_tasks.sh done <task-id> [--list <title-or-id>]

Mark one task completed. The task is addressed by the stable id printed by
`pull`, never by its position in the list.

Options:
  --list <title-or-id>   List the task belongs to (default: GOOGLE_TASKS_LIST)
USAGE
}

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

# Environment beats the config file, so a one-off override needs no edit.
load_config() {
  local env_client_json="${GOOGLE_TASKS_CLIENT_JSON:-}"
  local env_list="${GOOGLE_TASKS_LIST:-}"

  if [[ -f "$CONFIG_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090  # a user config location, not a repository file
    if ! . "$CONFIG_FILE"; then
      set +a
      die "could not read $CONFIG_FILE
It must be shell assignments with quoted values, e.g. GOOGLE_TASKS_LIST=\"App notes\".
Template: $SKILL_DIR/references/google-tasks.env.example"
    fi
    set +a
  fi

  [[ -n "$env_client_json" ]] && GOOGLE_TASKS_CLIENT_JSON="$env_client_json"
  [[ -n "$env_list" ]] && GOOGLE_TASKS_LIST="$env_list"

  GOOGLE_TASKS_CLIENT_JSON="${GOOGLE_TASKS_CLIENT_JSON:-$CONFIG_DIR/google-tasks-client.json}"
  GOOGLE_TASKS_LIST="${GOOGLE_TASKS_LIST:-@default}"
}

require_deps() {
  local missing=()
  local cmd
  for cmd in oauth2l jq curl; do
    command -v "$cmd" >/dev/null 2>&1 || missing+=("$cmd")
  done
  if ((${#missing[@]} > 0)); then
    die "missing on PATH: ${missing[*]} — install them first, see $SETUP_DOC"
  fi
}

require_client_json() {
  [[ -f "$GOOGLE_TASKS_CLIENT_JSON" ]] ||
    die "OAuth client JSON not found: $GOOGLE_TASKS_CLIENT_JSON
Put the file there, or set GOOGLE_TASKS_CLIENT_JSON. How to obtain it: $SETUP_DOC"
}

# One token per invocation; oauth2l caches the refresh token itself.
auth_header() {
  if [[ -z "$AUTH_HEADER" ]]; then
    local out
    out=$(timeout 60 oauth2l header \
      --credentials "$GOOGLE_TASKS_CLIENT_JSON" \
      --scope "$SCOPE" \
      --refresh \
      --disableAutoOpenConsentPage \
      --consentPageInteractionTimeout 1 \
      --consentPageInteractionTimeoutUnits seconds </dev/null 2>/dev/null || true)
    case "$out" in
      Authorization:*) AUTH_HEADER=$(printf '%s' "$out" | head -n 1) ;;
      *) die "no usable access token — run: google_tasks.sh auth" ;;
    esac
  fi
  printf '%s' "$AUTH_HEADER"
}

# Prints the response body. A non-2xx dies with the API's own error message.
api() {
  local method="$1" url="$2" body="${3:-}"
  local raw code response args=()

  args=(-sS -w $'\n%{http_code}' -X "$method"
    -H "$(auth_header)" -H 'Accept: application/json')
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$body")
  fi

  raw=$(curl "${args[@]}" "$url") || die "could not reach $url"
  code=${raw##*$'\n'}
  response=${raw%$'\n'*}

  if [[ "$code" != 2* ]]; then
    local message
    message=$(printf '%s' "$response" | jq -r '.error.message // empty' 2>/dev/null || true)
    [[ -n "$message" ]] || message="no error message in the response"
    die "google tasks api: HTTP $code: $message"
  fi

  printf '%s' "$response"
}

format_lists() {
  jq -r '
    (.items // []) as $lists
    | if ($lists | length) == 0
      then "No task lists on this account."
      else $lists[] | .id + "  " + (.title // "(untitled)")
      end
  '
}

format_tasks() {
  jq -r '
    def lines: gsub("\r"; "") | split("\n");
    (.items // []) as $tasks
    | if ($tasks | length) == 0
      then "No open tasks."
      else
        $tasks[]
        | (.notes // "") as $notes
        | [ (.title // "(untitled)"),
            "  id    " + .id,
            (if (.due // "") != "" then "  due   " + (.due | split("T")[0]) else empty end),
            (if $notes != "" then "  note  " + (($notes | lines)[0]) else empty end),
            (if $notes != "" then (($notes | lines)[1:][] | "        " + .) else empty end),
            ""
          ]
        | .[]
      end
  '
}

# Accepts a title or an id, prints "<id>\t<title>".
resolve_list() {
  local wanted="$1" resolved

  if [[ "$wanted" == "@default" ]]; then
    api GET "$API_ROOT/users/@me/lists/@default" |
      jq -r '.id + "\t" + (.title // "(untitled)")'
    return
  fi

  resolved=$(api GET "$API_ROOT/users/@me/lists?maxResults=1000" |
    jq -r --arg wanted "$wanted" '
      (.items // []) as $lists
      | ( [$lists[] | select(.title == $wanted)]
          + [$lists[] | select((.title // "" | ascii_downcase) == ($wanted | ascii_downcase))]
          + [$lists[] | select(.id == $wanted)]
        )
      | if length == 0 then empty
        else .[0] | .id + "\t" + (.title // "(untitled)")
        end
    ')

  [[ -n "$resolved" ]] || die "no task list matches \"$wanted\" — run: google_tasks.sh lists"
  printf '%s\n' "$resolved"
}

cmd_doctor() {
  local failed=0 found

  while (($# > 0)); do
    case "$1" in
      -h | --help)
        usage_doctor
        return 0
        ;;
      *) die "doctor: unknown option: $1" ;;
    esac
  done

  local cmd
  for cmd in oauth2l jq curl; do
    if found=$(command -v "$cmd" 2>/dev/null); then
      printf 'ok    %-18s %s\n' "$cmd" "$found"
    else
      printf 'FAIL  %-18s not on PATH — see %s\n' "$cmd" "$SETUP_DOC"
      failed=1
    fi
  done

  if [[ -f "$CONFIG_FILE" ]]; then
    printf 'ok    %-18s %s\n' "config" "$CONFIG_FILE"
  else
    printf 'none  %-18s %s (absent, defaults apply)\n' "config" "$CONFIG_FILE"
  fi

  local have_client=0
  if [[ -f "$GOOGLE_TASKS_CLIENT_JSON" ]]; then
    printf 'ok    %-18s %s\n' "client json" "$GOOGLE_TASKS_CLIENT_JSON"
    have_client=1
  else
    printf 'FAIL  %-18s not found: %s — see %s\n' \
      "client json" "$GOOGLE_TASKS_CLIENT_JSON" "$SETUP_DOC"
    failed=1
  fi

  local have_token=0
  if ((failed == 0 && have_client == 1)); then
    if AUTH_HEADER=$(auth_header 2>/dev/null); then
      printf 'ok    %-18s refreshed (token not shown)\n' "token"
      have_token=1
    else
      printf 'FAIL  %-18s no usable token — run: google_tasks.sh auth\n' "token"
      failed=1
    fi
  else
    printf 'skip  %-18s needs the checks above\n' "token"
    failed=1
  fi

  if ((have_token == 1)); then
    local resolved
    if resolved=$(resolve_list "$GOOGLE_TASKS_LIST" 2>&1); then
      printf 'ok    %-18s %s\n' "default list" "$(printf '%s' "$resolved" | tr '\t' ' ')"
    else
      printf 'FAIL  %-18s %s\n' "default list" "$resolved"
      failed=1
    fi
  else
    printf 'skip  %-18s needs a token\n' "default list"
  fi

  if ((failed != 0)); then
    printf '\nNot ready. Fix the FAIL lines above; setup is documented in %s\n' "$SETUP_DOC" >&2
    return 1
  fi
  printf '\nReady.\n'
}

cmd_auth() {
  while (($# > 0)); do
    case "$1" in
      -h | --help)
        usage_auth
        return 0
        ;;
      *) die "auth: unknown option: $1" ;;
    esac
  done

  require_deps
  require_client_json

  printf 'Opening consent for scope %s\n' "$SCOPE"
  printf 'Paste the link below into a browser and approve, then come back here.\n\n'

  # Auto-open does not work under WSL2. The token oauth2l prints is redacted:
  # a bare long token line is replaced, prose and the consent URL pass through.
  oauth2l fetch \
    --type oauth \
    --credentials "$GOOGLE_TASKS_CLIENT_JSON" \
    --scope "$SCOPE" \
    --disableAutoOpenConsentPage \
    --consentPageInteractionTimeout 120 \
    --consentPageInteractionTimeoutUnits seconds |
    sed -u -E 's/^[A-Za-z0-9._~+/-]{40,}=*$/<access token withheld>/'

  # oauth2l exits 0 even when consent never completed, so prove it separately.
  AUTH_HEADER=""
  if AUTH_HEADER=$(auth_header 2>/dev/null); then
    printf '\nConsent stored. Run: google_tasks.sh doctor\n'
    return 0
  fi
  die "
consent did not complete — no usable token afterwards. Retry, or see $SETUP_DOC"
}

cmd_lists() {
  local as_json=0
  while (($# > 0)); do
    case "$1" in
      -h | --help)
        usage_lists
        return 0
        ;;
      --json) as_json=1 ;;
      *) die "lists: unknown option: $1" ;;
    esac
    shift
  done

  require_deps
  require_client_json
  AUTH_HEADER=$(auth_header) || exit 1

  local response
  response=$(api GET "$API_ROOT/users/@me/lists?maxResults=1000") || exit 1
  if ((as_json == 1)); then
    printf '%s' "$response" | jq '.items // []'
  else
    printf '%s' "$response" | format_lists
  fi
}

cmd_pull() {
  local list="" max=100 as_json=0
  while (($# > 0)); do
    case "$1" in
      -h | --help)
        usage_pull
        return 0
        ;;
      --list)
        list="${2:-}"
        [[ -n "$list" ]] || die "pull: --list needs a title or id"
        shift
        ;;
      --max)
        max="${2:-}"
        [[ "$max" =~ ^[0-9]+$ ]] || die "pull: --max needs a number"
        ((max >= 1 && max <= 100)) || die "pull: --max must be between 1 and 100"
        shift
        ;;
      --json) as_json=1 ;;
      *) die "pull: unknown option: $1" ;;
    esac
    shift
  done

  require_deps
  require_client_json
  AUTH_HEADER=$(auth_header) || exit 1

  local resolved list_id list_title response
  resolved=$(resolve_list "${list:-$GOOGLE_TASKS_LIST}") || exit 1
  list_id=${resolved%%$'\t'*}
  list_title=${resolved#*$'\t'}

  # showCompleted defaults to true, so both flags are mandatory to get open tasks.
  response=$(api GET \
    "$API_ROOT/lists/$list_id/tasks?showCompleted=false&showHidden=false&maxResults=$max") || exit 1

  if ((as_json == 1)); then
    printf '%s' "$response" | jq '.items // []'
  else
    printf '%s\n\n' "$list_title"
    printf '%s' "$response" | format_tasks
  fi
}

cmd_done() {
  local list="" task=""
  while (($# > 0)); do
    case "$1" in
      -h | --help)
        usage_done
        return 0
        ;;
      --list)
        list="${2:-}"
        [[ -n "$list" ]] || die "done: --list needs a title or id"
        shift
        ;;
      -*) die "done: unknown option: $1" ;;
      *)
        [[ -z "$task" ]] || die "done: one task id at a time, got \"$task\" and \"$1\""
        task="$1"
        ;;
    esac
    shift
  done

  if [[ -z "$task" ]]; then
    usage_done >&2
    exit 1
  fi

  require_deps
  require_client_json
  AUTH_HEADER=$(auth_header) || exit 1

  local resolved list_id
  resolved=$(resolve_list "${list:-$GOOGLE_TASKS_LIST}") || exit 1
  list_id=${resolved%%$'\t'*}

  # `completed` is output-only; sending it is rejected.
  api PATCH "$API_ROOT/lists/$list_id/tasks/$task" '{"status":"completed"}' |
    jq -r '"done: " + (.title // .id)' || exit 1
}

main() {
  local command="${1:-}"
  [[ $# -gt 0 ]] && shift

  case "$command" in
    "" | -h | --help | help)
      usage
      return 0
      ;;
    doctor | auth | lists | pull | done) ;;
    *)
      usage >&2
      die "
unknown command: $command"
      ;;
  esac

  load_config
  "cmd_$command" "$@"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
