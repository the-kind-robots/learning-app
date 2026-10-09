#!/usr/bin/env bash
# PreToolUse(Bash). Token hygiene — see AGENTS.md, "# Token hygiene".
# Thin wrapper: the parsing lives in read-guard.py (needs a real shell tokenizer).
command -v python3 >/dev/null 2>&1 || exit 0
exec python3 -I "$(dirname "${BASH_SOURCE[0]}")/read-guard.py"
