#!/usr/bin/env bash
# Rename the running Claude Code session to "<number> <title>".
#
# Usage: rename_session.sh <issue-number> <issue-title>
#
# Mechanism — an undocumented Claude Code internal, measured on 2.1.263. The harness hands
# every child process a unix socket in CLAUDE_CODE_MESSAGING_SOCKET, a token in
# CLAUDE_CODE_MESSAGING_TOKEN and its id in CLAUDE_CODE_SESSION_ID. Two newline-terminated
# JSON lines on that socket do what /rename does:
#
#   {"type":"auth","token":"<token>"}
#   {"type":"control","action":"rename","name":"<name>","session_id":"<session id>"}
#
# Nothing comes back; the client just closes. Effect: transcript custom-title, the
# ~/.claude/sessions/<pid>.json name, the terminal title and the background-job state.
#
# Outside a Claude session (no socket variable, socket path gone, no python3) this is a
# silent no-op with exit 0. It never fails its caller: the issue, board item and branch are
# the work, the name is a convenience.
set -euo pipefail

[[ $# -ge 2 ]] || { echo "Usage: rename_session.sh <issue-number> <issue-title>" >&2; exit 2; }

number="$1"
shift
title="$*"

socket="${CLAUDE_CODE_MESSAGING_SOCKET:-}"
[[ -n "$socket" && -S "$socket" ]] || exit 0
command -v python3 >/dev/null 2>&1 || exit 0

# One line, printable characters only, single spaces, trimmed. Control characters become
# spaces first so a tab or newline inside the title still separates words.
name="$(printf '%s %s' "$number" "$title" | tr '\000-\037\177' ' ' | tr -s ' ')"
name="${name#"${name%%[![:space:]]*}"}"
name="${name%"${name##*[![:space:]]}"}"
[[ -n "$name" ]] || exit 0

# A refused or timed-out socket is a lost rename, not an error worth a traceback.
if SESSION_NAME="$name" python3 - "$socket" 2>/dev/null <<'PY'
import json, os, socket, sys

sock_path = sys.argv[1]
token = os.environ.get("CLAUDE_CODE_MESSAGING_TOKEN", "")
session_id = os.environ.get("CLAUDE_CODE_SESSION_ID", "")
name = os.environ["SESSION_NAME"]

lines = [
    {"type": "auth", "token": token},
    {"type": "control", "action": "rename", "name": name, "session_id": session_id},
]
with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
    s.settimeout(2)
    s.connect(sock_path)
    s.sendall("".join(json.dumps(line) + "\n" for line in lines).encode())
PY
then
  echo "Session Name: $name"
fi
