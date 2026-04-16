#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
LOW_LEVEL_SCRIPT="${HOME}/.codex/skills/windows-chrome-cdp/scripts/chrome_cdp.sh"
LOW_LEVEL_SCRIPT_DIR="${HOME}/.codex/skills/windows-chrome-cdp/scripts"
POWERSHELL_MONITOR_SCRIPT_WIN="$(wslpath -w "${LOW_LEVEL_SCRIPT_DIR}/cdp_monitor.ps1")"
POWERSHELL_CAPTURE_SCRIPT_WIN="$(wslpath -w "${LOW_LEVEL_SCRIPT_DIR}/cdp_capture_console.ps1")"
DEFAULT_PORT="9333"
LOCAL_BASE_URL="https://sprecha.local"
PROD_BASE_URL="https://sprecha.de"
LOCAL_SUBSTRING="sprecha.local"
PROD_SUBSTRING="sprecha.de"
MONITOR_BASE_DIR="/mnt/c/temp/windows-chrome-cdp-monitor"

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

resolve_page_target_id() {
  local port="$1"
  local url_substring="$2"
  bash "$LOW_LEVEL_SCRIPT" send \
    --port "$port" \
    --browser \
    --method Target.getTargets \
    --params '{}' \
    --timeout-sec 20 | jq -r --arg needle "$url_substring" '
      .result.targetInfos
      | map(select(.type == "page" and ((.url // "") | contains($needle))))
      | .[0].targetId
    '
}

resolve_page_ws_url() {
  local port="$1"
  local url_substring="$2"
  local target_id
  target_id="$(resolve_page_target_id "$port" "$url_substring")"
  [[ -n "$target_id" && "$target_id" != "null" ]] || return 1
  printf 'ws://127.0.0.1:%s/devtools/page/%s\n' "$port" "$target_id"
}

wait_for_page_ws_url() {
  local port="$1"
  local url_substring="$2"
  local attempts="${3:-40}"
  local delay_sec="${4:-0.25}"
  local ws_url=""

  for _ in $(seq 1 "$attempts"); do
    ws_url="$(resolve_page_ws_url "$port" "$url_substring" 2>/dev/null || true)"
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
  local url_substring="$1"
  shift
  parse_eval_args "$@"
  local ws_url params_json
  ws_url="$(wait_for_page_ws_url "$PORT" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
  params_json="$(jq -Rn --arg expr "$EXPRESSION" \
    '{expression:$expr, returnByValue:true, awaitPromise:true, userGesture:true}')"
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method Runtime.evaluate --params "$params_json"
}

start_env() {
  local base_url="$1"
  local url_substring="$2"
  shift
  shift
  parse_port_and_path "$@"
  local full_url="${base_url}${PATH_SUFFIX}"

  if bash "$LOW_LEVEL_SCRIPT" version --port "$PORT" >/tmp/learning-app-cdp-version.json 2>/dev/null; then
    bash "$LOW_LEVEL_SCRIPT" send \
      --port "$PORT" \
      --browser \
      --method Target.createTarget \
      --params "{\"url\":$(jq -Rn --arg url "$full_url" '$url')}" \
      --timeout-sec 20 >/dev/null
    cat /tmp/learning-app-cdp-version.json
    rm -f /tmp/learning-app-cdp-version.json
    return 0
  fi

  bash "$LOW_LEVEL_SCRIPT" start --port "$PORT" --url "$full_url"
  wait_for_page_ws_url "$PORT" "$url_substring" >/dev/null
}

monitor_env() {
  local url_substring="$1"
  shift
  parse_monitor_args "$@"
  if [[ -z "$SESSION" ]]; then
    SESSION="${url_substring}-${PORT}"
  fi
  local ws_url
  ws_url="$(wait_for_page_ws_url "$PORT" "$url_substring")"
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
  local url_substring="$1"
  shift
  parse_console_args "$@"
  local ws_url
  ws_url="$(wait_for_page_ws_url "$PORT" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"
  (
    cd /mnt/c
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$POWERSHELL_CAPTURE_SCRIPT_WIN" \
      -WebSocketUrl "$ws_url" \
      -TimeoutSeconds "$SECONDS"
  )
}

refresh_env() {
  local url_substring="$1"
  shift
  parse_refresh_args "$@"
  local ws_url
  ws_url="$(wait_for_page_ws_url "$PORT" "$url_substring")"
  [[ -n "$ws_url" ]] || die "Could not resolve page websocket URL"

  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method ServiceWorker.enable --params '{}'
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method ServiceWorker.setForceUpdateOnPageLoad --params '{"forceUpdateOnPageLoad":true}'
  bash "$LOW_LEVEL_SCRIPT" send --ws-url "$ws_url" --method Page.reload --params '{"ignoreCache":true}'
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

usage() {
  cat <<'EOF'
Usage:
  learning_app_cdp.sh start-local [--port PORT] [--path /route]
  learning_app_cdp.sh start-prod [--port PORT] [--path /route]
  learning_app_cdp.sh monitor-local [--port PORT] [--session NAME]
  learning_app_cdp.sh monitor-prod [--port PORT] [--session NAME]
  learning_app_cdp.sh console-local [--port PORT] [--seconds SEC]
  learning_app_cdp.sh console-prod [--port PORT] [--seconds SEC]
  learning_app_cdp.sh refresh-local [--port PORT]
  learning_app_cdp.sh refresh-prod [--port PORT]
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
    start-local)
      start_env "$LOCAL_BASE_URL" "$LOCAL_SUBSTRING" "$@"
      ;;
    start-prod)
      start_env "$PROD_BASE_URL" "$PROD_SUBSTRING" "$@"
      ;;
    monitor-local)
      monitor_env "$LOCAL_SUBSTRING" "$@"
      ;;
    monitor-prod)
      monitor_env "$PROD_SUBSTRING" "$@"
      ;;
    console-local)
      console_env "$LOCAL_SUBSTRING" "$@"
      ;;
    console-prod)
      console_env "$PROD_SUBSTRING" "$@"
      ;;
    refresh-local)
      refresh_env "$LOCAL_SUBSTRING" "$@"
      ;;
    refresh-prod)
      refresh_env "$PROD_SUBSTRING" "$@"
      ;;
    eval-local)
      runtime_eval_env "$LOCAL_SUBSTRING" "$@"
      ;;
    eval-prod)
      runtime_eval_env "$PROD_SUBSTRING" "$@"
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
