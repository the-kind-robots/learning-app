#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
LOW_LEVEL_SCRIPT="${HOME}/.codex/skills/windows-chrome-cdp/scripts/chrome_cdp.sh"
LOW_LEVEL_SCRIPT_DIR="${HOME}/.codex/skills/windows-chrome-cdp/scripts"
CHROME_PATH="/mnt/c/Program Files/Google/Chrome/Application/chrome.exe"
POWERSHELL_MONITOR_SCRIPT_WIN="$(wslpath -w "${LOW_LEVEL_SCRIPT_DIR}/cdp_monitor.ps1")"
POWERSHELL_CAPTURE_SCRIPT_WIN="$(wslpath -w "${LOW_LEVEL_SCRIPT_DIR}/cdp_capture_console.ps1")"
DEFAULT_PORT="9333"
LOCAL_BASE_URL="https://sprecha.local"
PROD_BASE_URL="https://sprecha.de"
LOCAL_SUBSTRING="sprecha.local"
PROD_SUBSTRING="sprecha.de"
MONITOR_BASE_DIR="/mnt/c/temp/windows-chrome-cdp-monitor"
STATE_BASE_DIR="/tmp/learning-app-cdp-state"

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

need_low_level_script() {
  [[ -x "$LOW_LEVEL_SCRIPT" ]] || [[ -f "$LOW_LEVEL_SCRIPT" ]] || die "Missing $LOW_LEVEL_SCRIPT"
}

sanitize_session_name() {
  local raw="$1"
  raw="${raw//[^A-Za-z0-9._-]/-}"
  printf '%s' "$raw"
}

monitor_paths() {
  local session="$1"
  local safe_session
  safe_session="$(sanitize_session_name "$session")"
  printf '%s\n' \
    "$MONITOR_BASE_DIR/${safe_session}.jsonl" \
    "$MONITOR_BASE_DIR/${safe_session}.state.json"
}

state_path() {
  local env_name="$1"
  local port="$2"
  mkdir -p "$STATE_BASE_DIR"
  printf '%s\n' "$STATE_BASE_DIR/${env_name}-${port}.json"
}

read_saved_target_id() {
  read_saved_state_field "$1" "$2" targetId
}

read_saved_path() {
  read_saved_state_field "$1" "$2" path
}

read_saved_state_field() {
  local env_name="$1"
  local port="$2"
  local field="$3"
  local path
  path="$(state_path "$env_name" "$port")"
  [[ -f "$path" ]] || return 0
  jq -r --arg field "$field" '.[$field] // empty' "$path" 2>/dev/null || true
}

write_saved_state() {
  local env_name="$1"
  local port="$2"
  local target_id="$3"
  local saved_path="${4:-}"
  local path
  path="$(state_path "$env_name" "$port")"
  jq -n --arg targetId "$target_id" --arg pathValue "$saved_path" '
    {targetId:$targetId}
    + (if ($pathValue | length) > 0 then {path:$pathValue} else {} end)
  ' >"$path"
}

write_saved_target_id() {
  local env_name="$1"
  local port="$2"
  local target_id="$3"
  local saved_path
  saved_path="$(read_saved_path "$env_name" "$port")"
  write_saved_state "$env_name" "$port" "$target_id" "$saved_path"
}

write_saved_path() {
  local env_name="$1"
  local port="$2"
  local saved_path="$3"
  local target_id
  target_id="$(read_saved_target_id "$env_name" "$port")"
  write_saved_state "$env_name" "$port" "$target_id" "$saved_path"
}

clear_saved_target_id() {
  local env_name="$1"
  local port="$2"
  rm -f "$(state_path "$env_name" "$port")"
}

page_targets_json() {
  local port="$1"
  local url_substring="$2"
  bash "$LOW_LEVEL_SCRIPT" send \
    --port "$port" \
    --browser \
    --method Target.getTargets \
    --params '{}' \
    --timeout-sec 20 | jq -c --arg needle "$url_substring" '
      .result.targetInfos
      | map(select(.type == "page" and ((.url // "") | contains($needle))))
    '
}

close_page_target() {
  local port="$1"
  local target_id="$2"
  bash "$LOW_LEVEL_SCRIPT" send \
    --port "$port" \
    --browser \
    --method Target.closeTarget \
    --params "{\"targetId\":$(jq -Rn --arg id "$target_id" '$id')}" \
    --timeout-sec 20 >/dev/null
}

pick_primary_target_id() {
  local targets_json="$1"
  local full_url="${2:-}"
  printf '%s\n' "$targets_json" | jq -r --arg url "$full_url" '
    if ($url | length) > 0 then
      (map(select((.url // "") == $url)) + map(select((.url // "") != $url)) | .[0].targetId)
    else
      .[0].targetId
    end // empty
  '
}

ensure_single_target() {
  local env_name="$1"
  local port="$2"
  local url_substring="$3"
  local full_url="${4:-}"
  local targets_json primary_target_id

  targets_json="$(page_targets_json "$port" "$url_substring")"
  primary_target_id="$(pick_primary_target_id "$targets_json" "$full_url")"

  [[ -n "$primary_target_id" ]] || return 1

  while IFS= read -r extra_target_id; do
    [[ -n "$extra_target_id" ]] || continue
    [[ "$extra_target_id" == "$primary_target_id" ]] && continue
    close_page_target "$port" "$extra_target_id"
  done < <(printf '%s\n' "$targets_json" | jq -r '.[].targetId')

  write_saved_target_id "$env_name" "$port" "$primary_target_id"
  printf '%s\n' "$primary_target_id"
}

resolve_saved_or_single_target_id() {
  local env_name="$1"
  local port="$2"
  local url_substring="$3"
  local saved_target_id targets_json count matched_target_id

  saved_target_id="$(read_saved_target_id "$env_name" "$port")"
  if [[ -n "$saved_target_id" ]]; then
    matched_target_id="$(page_targets_json "$port" "$url_substring" | jq -r --arg id "$saved_target_id" '
      map(select(.targetId == $id)) | .[0].targetId // empty
    ')"
    if [[ -n "$matched_target_id" ]]; then
      printf '%s\n' "$matched_target_id"
      return 0
    fi
    clear_saved_target_id "$env_name" "$port"
  fi

  targets_json="$(page_targets_json "$port" "$url_substring")"
  count="$(printf '%s\n' "$targets_json" | jq 'length')"
  if [[ "$count" == "1" ]]; then
    matched_target_id="$(printf '%s\n' "$targets_json" | jq -r '.[0].targetId')"
    write_saved_target_id "$env_name" "$port" "$matched_target_id"
    printf '%s\n' "$matched_target_id"
    return 0
  fi

  return 1
}

resolve_page_target_id() {
  local port="$1"
  local env_name="$2"
  local url_substring="$3"
  resolve_saved_or_single_target_id "$env_name" "$port" "$url_substring"
}

resolve_page_ws_url() {
  local port="$1"
  local env_name="$2"
  local url_substring="$3"
  local target_id
  target_id="$(resolve_page_target_id "$port" "$env_name" "$url_substring")"
  [[ -n "$target_id" && "$target_id" != "null" ]] || return 1
  printf 'ws://127.0.0.1:%s/devtools/page/%s\n' "$port" "$target_id"
}

wait_for_page_ws_url() {
  local port="$1"
  local env_name="$2"
  local url_substring="$3"
  local attempts="${4:-40}"
  local delay_sec="${5:-0.25}"
  local ws_url=""

  for _ in $(seq 1 "$attempts"); do
    ws_url="$(resolve_page_ws_url "$port" "$env_name" "$url_substring" 2>/dev/null || true)"
    if [[ -n "$ws_url" ]]; then
      printf '%s\n' "$ws_url"
      return 0
    fi
    sleep "$delay_sec"
  done

  return 1
}

parse_port_and_path() {
  PORT="$DEFAULT_PORT"
  PATH_SUFFIX=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        PORT="${2:?missing port}"
        shift 2
        ;;
      --path)
        PATH_SUFFIX="${2:?missing path}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done
}

parse_monitor_args() {
  PORT="$DEFAULT_PORT"
  SESSION=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        PORT="${2:?missing port}"
        shift 2
        ;;
      --session)
        SESSION="${2:?missing session}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done
}

parse_console_args() {
  PORT="$DEFAULT_PORT"
  SECONDS="4"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        PORT="${2:?missing port}"
        shift 2
        ;;
      --seconds)
        SECONDS="${2:?missing seconds}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done
}

parse_refresh_args() {
  PORT="$DEFAULT_PORT"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        PORT="${2:?missing port}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done
}

parse_wait_args() {
  PORT="$DEFAULT_PORT"
  WAIT_PATH=""
  WAIT_SELECTOR=""
  TIMEOUT_SEC="10"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        PORT="${2:?missing port}"
        shift 2
        ;;
      --path)
        WAIT_PATH="${2:?missing path}"
        shift 2
        ;;
      --selector)
        WAIT_SELECTOR="${2:?missing selector}"
        shift 2
        ;;
      --timeout-sec)
        TIMEOUT_SEC="${2:?missing timeout}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done
}

parse_eval_args() {
  PORT="$DEFAULT_PORT"
  EXPRESSION=""
  FILE_PATH=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        PORT="${2:?missing port}"
        shift 2
        ;;
      --expression)
        EXPRESSION="${2:?missing expression}"
        shift 2
        ;;
      --file)
        FILE_PATH="${2:?missing file path}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done

  if [[ -n "$FILE_PATH" ]]; then
    [[ -f "$FILE_PATH" ]] || die "Missing script file: $FILE_PATH"
    EXPRESSION="$(cat "$FILE_PATH")"
  fi

  [[ -n "$EXPRESSION" ]] || die "Provide --expression or --file"
}

runtime_eval_env() {
  local env_name="$1"
  local url_substring="$2"
  shift
  shift
  parse_eval_args "$@"
  local ws_url params_json
  ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
  params_json="$(jq -Rn --arg expr "$EXPRESSION" \
    '{expression:$expr, returnByValue:true, awaitPromise:true, userGesture:true}')"
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method Runtime.evaluate --params "$params_json"
}

current_path_via_ws_url() {
  local ws_url="$1"
  local params_json
  params_json="$(jq -Rn --arg expr 'location.pathname' \
    '{expression:$expr, returnByValue:true, awaitPromise:true, userGesture:true}')"
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method Runtime.evaluate --params "$params_json" \
    | jq -r '.result.result.value // empty'
}

wait_for_path() {
  local port="$1"
  local env_name="$2"
  local url_substring="$3"
  local expected_path="$4"
  local timeout_sec="${5:-10}"
  local deadline ws_url current_path

  deadline=$((SECONDS + timeout_sec))
  while :; do
    ws_url="$(wait_for_page_ws_url "$port" "$env_name" "$url_substring")"
    [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
    current_path="$(current_path_via_ws_url "$ws_url")"
    if [[ "$current_path" == "$expected_path" ]]; then
      return 0
    fi
    if (( SECONDS >= deadline )); then
      die "Timed out waiting for path $expected_path"
    fi
    sleep 0.25
  done
}

start_env() {
  local env_name="$1"
  local base_url="$2"
  local url_substring="$3"
  shift
  shift
  shift
  parse_port_and_path "$@"
  local full_url="${base_url}${PATH_SUFFIX}"
  local saved_path="${PATH_SUFFIX:-}"

  if bash "$LOW_LEVEL_SCRIPT" version --port "$PORT" >/tmp/learning-app-cdp-version.json 2>/dev/null; then
    bash "$LOW_LEVEL_SCRIPT" send \
      --port "$PORT" \
      --browser \
      --method Target.createTarget \
      --params "{\"url\":$(jq -Rn --arg url "$full_url" '$url')}" \
      --timeout-sec 20 >/dev/null
    ensure_single_target "$env_name" "$PORT" "$url_substring" "$full_url" >/dev/null
    write_saved_path "$env_name" "$PORT" "$saved_path"
    if [[ -n "$saved_path" ]]; then
      wait_for_path "$PORT" "$env_name" "$url_substring" "$saved_path"
    fi
    cat /tmp/learning-app-cdp-version.json
    rm -f /tmp/learning-app-cdp-version.json
    return 0
  fi

  bash "$LOW_LEVEL_SCRIPT" start --port "$PORT" --url "$full_url"
  ensure_single_target "$env_name" "$PORT" "$url_substring" "$full_url" >/dev/null
  write_saved_path "$env_name" "$PORT" "$saved_path"
  if [[ -n "$saved_path" ]]; then
    wait_for_path "$PORT" "$env_name" "$url_substring" "$saved_path"
  else
    wait_for_page_ws_url "$PORT" "$env_name" "$url_substring" >/dev/null
  fi
}

monitor_env() {
  local env_name="$1"
  local url_substring="$2"
  shift
  shift
  parse_monitor_args "$@"
  if [[ -z "$SESSION" ]]; then
    SESSION="${url_substring}-${PORT}"
  fi
  local ws_url
  ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"

  mkdir -p "$MONITOR_BASE_DIR"
  local buffer_path state_path buffer_path_win state_path_win
  mapfile -t _paths < <(monitor_paths "$SESSION")
  buffer_path="${_paths[0]}"
  state_path="${_paths[1]}"
  buffer_path_win="$(wslpath -w "$buffer_path")"
  state_path_win="$(wslpath -w "$state_path")"

  if [[ -f "$state_path" ]]; then
    local existing_pid=""
    existing_pid="$(jq -r '.pid // empty' "$state_path" 2>/dev/null || true)"
    if [[ -n "$existing_pid" ]]; then
      if (
        cd /mnt/c
        cmd.exe /c tasklist /FI "PID eq ${existing_pid}" 2>/dev/null
      ) | tr -d '\r' | grep -q "$existing_pid"; then
        die "Monitor session '$SESSION' is already running"
      fi
    fi
  fi

  : >"$buffer_path"
  (
    cd /mnt/c
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$POWERSHELL_MONITOR_SCRIPT_WIN" \
      -Mode start \
      -WebSocketUrl "$ws_url" \
      -BufferPath "$buffer_path_win" \
      -StatePath "$state_path_win"
  )
}

console_env() {
  local env_name="$1"
  local url_substring="$2"
  shift
  shift
  parse_console_args "$@"
  local ws_url
  ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
  (
    cd /mnt/c
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$POWERSHELL_CAPTURE_SCRIPT_WIN" \
      -WebSocketUrl "$ws_url" \
      -TimeoutSeconds "$SECONDS"
  )
}

refresh_env() {
  local env_name="$1"
  local base_url="$2"
  local url_substring="$3"
  shift
  shift
  shift
  parse_refresh_args "$@"
  local ws_url saved_path current_path
  ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"

  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method ServiceWorker.enable --params '{}'
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method ServiceWorker.setForceUpdateOnPageLoad --params '{"forceUpdateOnPageLoad":true}'
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method Page.reload --params '{"ignoreCache":true}'

  saved_path="$(read_saved_path "$env_name" "$PORT")"
  [[ -n "$saved_path" ]] || return 0

  ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
  current_path="$(current_path_via_ws_url "$ws_url")"
  if [[ "$current_path" != "$saved_path" ]]; then
    start_env "$env_name" "$base_url" "$url_substring" --port "$PORT" --path "$saved_path" >/dev/null
  fi
}

wait_env() {
  local env_name="$1"
  local url_substring="$2"
  shift
  shift
  parse_wait_args "$@"
  if [[ -n "$WAIT_PATH" ]]; then
    write_saved_path "$env_name" "$PORT" "$WAIT_PATH"
  fi

  local ws_url expression params_json
  ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"

  expression="$(jq -rn --arg path "$WAIT_PATH" --arg selector "$WAIT_SELECTOR" '
    "(() => { " +
    "const expectedPath = " + ($path | @json) + "; " +
    "const selector = " + ($selector | @json) + "; " +
    "const pathOk = !expectedPath || location.pathname === expectedPath; " +
    "const selectorOk = !selector || !!document.querySelector(selector); " +
    "return {path: location.pathname, pathOk, selectorOk}; " +
    "})()"
  ')"

  params_json="$(jq -Rn --arg expr "$expression" \
    '{expression:$expr, returnByValue:true, awaitPromise:true, userGesture:true}')"

  local deadline now response path_ok selector_ok
  deadline=$((SECONDS + TIMEOUT_SEC))

  while :; do
    response="$(bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method Runtime.evaluate --params "$params_json")"
    path_ok="$(printf '%s\n' "$response" | jq -r '.result.result.value.pathOk // false')"
    selector_ok="$(printf '%s\n' "$response" | jq -r '.result.result.value.selectorOk // false')"
    if [[ "$path_ok" == "true" && "$selector_ok" == "true" ]]; then
      printf '%s\n' "$response"
      return 0
    fi

    now=$SECONDS
    if (( now >= deadline )); then
      die "Timed out waiting for page state"
    fi

    sleep 0.25
    ws_url="$(wait_for_page_ws_url "$PORT" "$env_name" "$url_substring")"
    [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
  done
}

read_monitor() {
  local session=""
  local tail_lines=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --session)
        session="${2:?missing session}"
        shift 2
        ;;
      --tail)
        tail_lines="${2:?missing tail lines}"
        shift 2
        ;;
      *)
        die "Unknown option: $1"
        ;;
    esac
  done

  [[ -n "$session" ]] || die "--session is required"
  if [[ -n "$tail_lines" ]]; then
    bash "$LOW_LEVEL_SCRIPT" monitor-read --session "$session" --tail "$tail_lines"
  else
    bash "$LOW_LEVEL_SCRIPT" monitor-read --session "$session"
  fi
}

clear_monitor() {
  [[ "${1:-}" == "--session" ]] || die "Usage: clear --session NAME"
  bash "$LOW_LEVEL_SCRIPT" monitor-clear --session "${2:?missing session}"
}

stop_monitor() {
  [[ "${1:-}" == "--session" ]] || die "Usage: stop --session NAME"
  bash "$LOW_LEVEL_SCRIPT" monitor-stop --session "${2:?missing session}"
}


doctor() {
  local failures=0

  echo "learning-app-cdp doctor"

  if grep -qi microsoft /proc/version 2>/dev/null; then
    echo "ok  WSL kernel detected"
  else
    echo "warn WSL kernel not detected"
  fi

  if [[ -n "${WSL_INTEROP:-}" && -S "${WSL_INTEROP:-}" ]]; then
    echo "ok  WSL_INTEROP socket exists: ${WSL_INTEROP}"
  else
    echo "fail WSL_INTEROP socket missing"
    failures=1
  fi

  if [[ -e /proc/sys/fs/binfmt_misc/WSLInterop ]]; then
    if grep -q '^enabled' /proc/sys/fs/binfmt_misc/WSLInterop 2>/dev/null; then
      echo "ok  WSLInterop binfmt handler is enabled"
    else
      echo "fail WSLInterop binfmt handler exists but is disabled"
      failures=1
    fi
  else
    echo "fail WSLInterop binfmt handler is missing"
    failures=1
  fi

  if [[ -f /mnt/c/WINDOWS/system32/cmd.exe ]]; then
    echo "ok  Windows cmd.exe is visible"
  else
    echo "fail Windows cmd.exe is not visible at /mnt/c/WINDOWS/system32/cmd.exe"
    failures=1
  fi

  local tmp_out tmp_err
  tmp_out="$(mktemp)"
  tmp_err="$(mktemp)"
  if timeout 5s cmd.exe /c ver >"$tmp_out" 2>"$tmp_err"; then
    echo "ok  Windows cmd.exe launch works"
    sed -n '1,2p' "$tmp_out"
  else
    echo "fail Windows cmd.exe launch failed"
    sed -n '1,4p' "$tmp_err"
    failures=1
  fi
  rm -f "$tmp_out" "$tmp_err"

  tmp_out="$(mktemp)"
  tmp_err="$(mktemp)"
  if timeout 5s powershell.exe -NoProfile -Command '$PSVersionTable.PSVersion.ToString()' >"$tmp_out" 2>"$tmp_err"; then
    echo "ok  Windows powershell.exe launch works"
    sed -n '1,1p' "$tmp_out"
  else
    echo "fail Windows powershell.exe launch failed"
    sed -n '1,4p' "$tmp_err"
    failures=1
  fi
  rm -f "$tmp_out" "$tmp_err"

  tmp_out="$(mktemp)"
  tmp_err="$(mktemp)"
  if (cd /mnt/c && timeout 5s cmd.exe /c curl.exe -fsS http://127.0.0.1:9 >"$tmp_out" 2>"$tmp_err"); then
    echo "ok  Windows curl.exe launch works"
  else
    local curl_exit=$?
    if [[ "$curl_exit" -eq 7 ]]; then
      echo "ok  Windows curl.exe launch works"
    else
      echo "fail Windows curl.exe launch failed"
      sed -n '1,4p' "$tmp_err"
      failures=1
    fi
  fi
  rm -f "$tmp_out" "$tmp_err"

  if [[ -x "$CHROME_PATH" ]]; then
    echo "ok  Windows Chrome is visible: $CHROME_PATH"
  else
    echo "fail Windows Chrome not found at $CHROME_PATH"
    failures=1
  fi

  if [[ "$failures" -ne 0 ]]; then
    cat <<'EOF'

Diagnosis:
  Windows CDP needs WSLInterop binfmt support so Linux can hand .exe launches
  to /init. If the WSLInterop handler is missing or disabled, commands like
  cmd.exe, powershell.exe, and Windows Chrome cannot run from this session.

Repair:
  Run `wsl --shutdown` from Windows, reopen WSL, then rerun this doctor.

Fallback:
  For scripted browser invariants, use committed repo tests or purpose-built
  repo verifiers instead of ad-hoc CDP snippets.
EOF
  fi

  return "$failures"
}

usage() {
  cat <<'EOF'
Usage:
  learning_app_cdp.sh doctor
  learning_app_cdp.sh start-local [--port PORT] [--path /route]
  learning_app_cdp.sh start-prod [--port PORT] [--path /route]
  learning_app_cdp.sh monitor-local [--port PORT] [--session NAME]
  learning_app_cdp.sh monitor-prod [--port PORT] [--session NAME]
  learning_app_cdp.sh console-local [--port PORT] [--seconds SEC]
  learning_app_cdp.sh console-prod [--port PORT] [--seconds SEC]
  learning_app_cdp.sh refresh-local [--port PORT]
  learning_app_cdp.sh refresh-prod [--port PORT]
  learning_app_cdp.sh wait-local [--port PORT] [--path /route] [--selector CSS] [--timeout-sec N]
  learning_app_cdp.sh wait-prod [--port PORT] [--path /route] [--selector CSS] [--timeout-sec N]
  learning_app_cdp.sh eval-local [--port PORT] (--expression JS | --file FILE)
  learning_app_cdp.sh eval-prod [--port PORT] (--expression JS | --file FILE)
  learning_app_cdp.sh read --session NAME [--tail N]
  learning_app_cdp.sh clear --session NAME
  learning_app_cdp.sh stop --session NAME
EOF
}

main() {
  need_low_level_script
  local subcommand="${1:-}"
  [[ -n "$subcommand" ]] || {
    usage
    exit 1
  }
  shift

  case "$subcommand" in
    doctor)
      doctor
      ;;
    start-local)
      start_env "local" "$LOCAL_BASE_URL" "$LOCAL_SUBSTRING" "$@"
      ;;
    start-prod)
      start_env "prod" "$PROD_BASE_URL" "$PROD_SUBSTRING" "$@"
      ;;
    monitor-local)
      monitor_env "local" "$LOCAL_SUBSTRING" "$@"
      ;;
    monitor-prod)
      monitor_env "prod" "$PROD_SUBSTRING" "$@"
      ;;
    console-local)
      console_env "local" "$LOCAL_SUBSTRING" "$@"
      ;;
    console-prod)
      console_env "prod" "$PROD_SUBSTRING" "$@"
      ;;
    refresh-local)
      refresh_env "local" "$LOCAL_BASE_URL" "$LOCAL_SUBSTRING" "$@"
      ;;
    refresh-prod)
      refresh_env "prod" "$PROD_BASE_URL" "$PROD_SUBSTRING" "$@"
      ;;
    wait-local)
      wait_env "local" "$LOCAL_SUBSTRING" "$@"
      ;;
    wait-prod)
      wait_env "prod" "$PROD_SUBSTRING" "$@"
      ;;
    eval-local)
      runtime_eval_env "local" "$LOCAL_SUBSTRING" "$@"
      ;;
    eval-prod)
      runtime_eval_env "prod" "$PROD_SUBSTRING" "$@"
      ;;
    read)
      read_monitor "$@"
      ;;
    clear)
      clear_monitor "$@"
      ;;
    stop)
      stop_monitor "$@"
      ;;
    *)
      usage
      die "Unknown subcommand: $subcommand"
      ;;
  esac
}

main "$@"
