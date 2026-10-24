#!/usr/bin/env bash
set -euo pipefail

CHROME_PATH='/mnt/c/Program Files/Google/Chrome/Application/chrome.exe'
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
POWERSHELL_SCRIPT_WIN="$(wslpath -w "$SCRIPT_DIR/cdp_send.ps1")"
POWERSHELL_CAPTURE_SCRIPT_WIN="$(wslpath -w "$SCRIPT_DIR/cdp_capture_console.ps1")"
POWERSHELL_MONITOR_SCRIPT_WIN="$(wslpath -w "$SCRIPT_DIR/cdp_monitor.ps1")"
MONITOR_BASE_DIR="/mnt/c/temp/windows-chrome-cdp-monitor"

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

win_curl() {
  (
    cd /mnt/c
    cmd.exe /c curl.exe -fsS "$@"
  )
}

version_json() {
  local port="$1"
  win_curl "http://127.0.0.1:${port}/json/version"
}

list_json() {
  local port="$1"
  win_curl "http://127.0.0.1:${port}/json/list"
}

browser_ws_url() {
  local port="$1"
  version_json "$port" | jq -r '.webSocketDebuggerUrl'
}

browser_send_json() {
  local port="$1"
  local method="$2"
  local params="${3:-{}}"
  bash "$SCRIPT_DIR/chrome_cdp.sh" send --port "$port" --browser --method "$method" --params "$params" --timeout-sec 20
}

page_target_id() {
  local port="$1"
  local index="${2:-0}"
  local url_substring="${3:-}"

  if [[ -n "$url_substring" ]]; then
    browser_send_json "$port" "Target.getTargets" '{}' | jq -r --arg needle "$url_substring" '
      .result.targetInfos
      | map(select(.type == "page" and ((.url // "") | contains($needle))))
      | .[0].targetId
    '
  else
    browser_send_json "$port" "Target.getTargets" '{}' | jq -r --argjson idx "$index" '
      .result.targetInfos
      | map(select(.type == "page"))
      | .[$idx].targetId
    '
  fi
}

page_ws_url() {
  local port="$1"
  local index="${2:-0}"
  local url_substring="${3:-}"

  local target_id
  target_id="$(page_target_id "$port" "$index" "$url_substring")"
  [[ -n "$target_id" && "$target_id" != "null" ]] || return 0
  printf 'ws://127.0.0.1:%s/devtools/page/%s\n' "$port" "$target_id"
}

start_chrome() {
  local port="9333"
  local profile_dir=""
  local open_url="about:blank"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        port="${2:?missing port}"
        shift 2
        ;;
      --profile-dir)
        profile_dir="${2:?missing profile dir}"
        shift 2
        ;;
      --url)
        open_url="${2:?missing url}"
        shift 2
        ;;
      *)
        die "Unknown start option: $1"
        ;;
    esac
  done

  [[ -x "$CHROME_PATH" ]] || die "Chrome not found at $CHROME_PATH"
  if [[ -z "$profile_dir" ]]; then
    profile_dir="C:\\temp\\chrome-debug-${port}-$(date +%s)"
  fi

  if version_json "$port" >/tmp/windows-chrome-cdp-version.json 2>/dev/null; then
    if [[ -n "$open_url" && "$open_url" != "about:blank" ]]; then
      browser_send_json "$port" "Target.createTarget" "{\"url\":$(jq -Rn --arg url "$open_url" '$url')}" >/dev/null
    fi
    cat /tmp/windows-chrome-cdp-version.json
    rm -f /tmp/windows-chrome-cdp-version.json
    return 0
  fi

  "$CHROME_PATH" \
    --remote-debugging-port="$port" \
    --user-data-dir="$profile_dir" \
    --no-first-run \
    --no-default-browser-check \
    --new-window \
    "$open_url" >/dev/null 2>&1 &

  for _ in $(seq 1 40); do
    if version_json "$port" >/tmp/windows-chrome-cdp-version.json 2>/dev/null; then
      cat /tmp/windows-chrome-cdp-version.json
      rm -f /tmp/windows-chrome-cdp-version.json
      return 0
    fi
    sleep 0.25
  done

  die "Chrome debug endpoint on port $port did not become ready"
}

send_cdp() {
  local port=""
  local use_browser=0
  local use_page=0
  local page_index="0"
  local url_substring=""
  local ws_url=""
  local method=""
  local params='{}'
  local id="1"
  local timeout_sec="10"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        port="${2:?missing port}"
        shift 2
        ;;
      --browser)
        use_browser=1
        shift
        ;;
      --page)
        use_page=1
        shift
        ;;
      --index)
        page_index="${2:?missing index}"
        shift 2
        ;;
      --url-substring)
        url_substring="${2:?missing substring}"
        shift 2
        ;;
      --ws-url)
        ws_url="${2:?missing ws url}"
        shift 2
        ;;
      --method)
        method="${2:?missing method}"
        shift 2
        ;;
      --params)
        params="${2:?missing params}"
        shift 2
        ;;
      --id)
        id="${2:?missing id}"
        shift 2
        ;;
      --timeout-sec)
        timeout_sec="${2:?missing timeout}"
        shift 2
        ;;
      *)
        die "Unknown send option: $1"
        ;;
    esac
  done

  [[ -n "$method" ]] || die "--method is required"

  if [[ -z "$ws_url" ]]; then
    [[ -n "$port" ]] || die "Provide --ws-url or --port"
    if [[ "$use_browser" -eq 1 ]]; then
      ws_url="$(browser_ws_url "$port")"
    elif [[ "$use_page" -eq 1 ]]; then
      ws_url="$(page_ws_url "$port" "$page_index" "$url_substring")"
    else
      die "When using --port, choose --browser or --page"
    fi
  fi

  [[ -n "$ws_url" && "$ws_url" != "null" ]] || die "Could not resolve websocket URL"

  (
    cd /mnt/c
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$POWERSHELL_SCRIPT_WIN" \
      -WebSocketUrl "$ws_url" \
      -Method "$method" \
      -ParamsJson "$params" \
      -Id "$id" \
      -TimeoutSeconds "$timeout_sec"
  )
}

console_log() {
  local port=""
  local index="0"
  local url_substring=""
  local seconds="4"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        port="${2:?missing port}"
        shift 2
        ;;
      --index)
        index="${2:?missing index}"
        shift 2
        ;;
      --url-substring)
        url_substring="${2:?missing substring}"
        shift 2
        ;;
      --seconds)
        seconds="${2:?missing seconds}"
        shift 2
        ;;
      *)
        die "Unknown console-log option: $1"
        ;;
    esac
  done

  [[ -n "$port" ]] || die "--port is required"
  local ws_url
  ws_url="$(page_ws_url "$port" "$index" "$url_substring")"
  [[ -n "$ws_url" && "$ws_url" != "null" ]] || die "Could not resolve page websocket URL"
  (
    cd /mnt/c
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$POWERSHELL_CAPTURE_SCRIPT_WIN" \
      -WebSocketUrl "$ws_url" \
      -TimeoutSeconds "$seconds"
  )
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

monitor_start() {
  local port=""
  local index="0"
  local url_substring=""
  local session=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --port)
        port="${2:?missing port}"
        shift 2
        ;;
      --index)
        index="${2:?missing index}"
        shift 2
        ;;
      --url-substring)
        url_substring="${2:?missing substring}"
        shift 2
        ;;
      --session)
        session="${2:?missing session}"
        shift 2
        ;;
      *)
        die "Unknown monitor-start option: $1"
        ;;
    esac
  done

  [[ -n "$port" ]] || die "--port is required"
  local ws_url
  ws_url="$(page_ws_url "$port" "$index" "$url_substring")"
  [[ -n "$ws_url" && "$ws_url" != "null" ]] || die "Could not resolve page websocket URL"

  if [[ -z "$session" ]]; then
    session="port-${port}-page-${index}"
  fi

  mkdir -p "$MONITOR_BASE_DIR"
  local buffer_path state_path buffer_path_win state_path_win
  mapfile -t _paths < <(monitor_paths "$session")
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
        die "Monitor session '$session' is already running"
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

monitor_read() {
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
        die "Unknown monitor-read option: $1"
        ;;
    esac
  done

  [[ -n "$session" ]] || die "--session is required"
  local buffer_path state_path
  mapfile -t _paths < <(monitor_paths "$session")
  buffer_path="${_paths[0]}"
  state_path="${_paths[1]}"
  [[ -f "$state_path" || -f "$buffer_path" ]] || die "Unknown monitor session '$session'"

  if [[ ! -s "$buffer_path" ]]; then
    printf '[]\n'
    return 0
  fi

  if [[ -n "$tail_lines" ]]; then
    tail -n "$tail_lines" "$buffer_path" | jq -s '.'
  else
    jq -s '.' "$buffer_path"
  fi
}

monitor_clear() {
  local session=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --session)
        session="${2:?missing session}"
        shift 2
        ;;
      *)
        die "Unknown monitor-clear option: $1"
        ;;
    esac
  done

  [[ -n "$session" ]] || die "--session is required"
  local buffer_path
  mapfile -t _paths < <(monitor_paths "$session")
  buffer_path="${_paths[0]}"
  : >"$buffer_path"
  printf '{"cleared":true,"session":"%s"}\n' "$session"
}

monitor_stop() {
  local session=""

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --session)
        session="${2:?missing session}"
        shift 2
        ;;
      *)
        die "Unknown monitor-stop option: $1"
        ;;
    esac
  done

  [[ -n "$session" ]] || die "--session is required"
  local buffer_path state_path
  mapfile -t _paths < <(monitor_paths "$session")
  buffer_path="${_paths[0]}"
  state_path="${_paths[1]}"
  [[ -f "$state_path" ]] || die "Unknown monitor session '$session'"

  local pid=""
  pid="$(jq -r '.pid // empty' "$state_path")"
  if [[ -n "$pid" ]]; then
    (
      cd /mnt/c
      cmd.exe /c taskkill /PID "$pid" /T /F
    ) >/dev/null 2>&1 || true
  fi

  rm -f "$state_path"
  printf '{"stopped":true,"session":"%s","buffer":"%s"}\n' "$session" "$buffer_path"
}

usage() {
  cat <<'EOF'
Usage:
  chrome_cdp.sh start [--port PORT] [--profile-dir WIN_PATH] [--url URL]
  chrome_cdp.sh version --port PORT
  chrome_cdp.sh list --port PORT
  chrome_cdp.sh browser-ws-url --port PORT
  chrome_cdp.sh page-ws-url --port PORT [--index N] [--url-substring TEXT]
  chrome_cdp.sh send (--ws-url URL | --port PORT --browser | --port PORT --page) --method NAME [--params JSON] [--id N] [--timeout-sec SEC]
  chrome_cdp.sh console-log --port PORT [--index N] [--url-substring TEXT] [--seconds SEC]
  chrome_cdp.sh monitor-start --port PORT [--index N] [--url-substring TEXT] [--session NAME]
  chrome_cdp.sh monitor-read --session NAME [--tail N]
  chrome_cdp.sh monitor-clear --session NAME
  chrome_cdp.sh monitor-stop --session NAME
EOF
}

main() {
  need_cmd jq
  local subcommand="${1:-}"
  [[ -n "$subcommand" ]] || {
    usage
    exit 1
  }
  shift

  case "$subcommand" in
    start)
      start_chrome "$@"
      ;;
    version)
      [[ "${1:-}" == "--port" ]] || die "Usage: chrome_cdp.sh version --port PORT"
      version_json "${2:?missing port}"
      ;;
    list)
      [[ "${1:-}" == "--port" ]] || die "Usage: chrome_cdp.sh list --port PORT"
      list_json "${2:?missing port}"
      ;;
    browser-ws-url)
      [[ "${1:-}" == "--port" ]] || die "Usage: chrome_cdp.sh browser-ws-url --port PORT"
      browser_ws_url "${2:?missing port}"
      ;;
    page-ws-url)
      [[ "${1:-}" == "--port" ]] || die "Usage: chrome_cdp.sh page-ws-url --port PORT [--index N] [--url-substring TEXT]"
      local port="${2:?missing port}"
      shift 2
      local index="0"
      local url_substring=""
      while [[ $# -gt 0 ]]; do
        case "$1" in
          --index)
            index="${2:?missing index}"
            shift 2
            ;;
          --url-substring)
            url_substring="${2:?missing substring}"
            shift 2
            ;;
          *)
            die "Unknown page-ws-url option: $1"
            ;;
        esac
      done
      page_ws_url "$port" "$index" "$url_substring"
      ;;
    send)
      send_cdp "$@"
      ;;
    console-log)
      console_log "$@"
      ;;
    monitor-start)
      monitor_start "$@"
      ;;
    monitor-read)
      monitor_read "$@"
      ;;
    monitor-clear)
      monitor_clear "$@"
      ;;
    monitor-stop)
      monitor_stop "$@"
      ;;
    *)
      usage
      die "Unknown subcommand: $subcommand"
      ;;
  esac
}

main "$@"
