#!/usr/bin/env bash
# PreToolUse(Bash). Token hygiene — see AGENTS.md, "# Token hygiene".
#
# Refuses `cat`/`less`/`more` of one existing file longer than 300 lines when the output
# is not piped into a filter. Read with offset/limit, or `grep -n ... | head`, costs a
# fraction of the context. Anything else passes silently.
set -uo pipefail
set -f

command -v jq >/dev/null 2>&1 || exit 0

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null || true)
[ -n "$cmd" ] || exit 0
cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null || true)
[ -n "$cwd" ] || cwd=$PWD

# One simple statement only: a pipe, a chain or a heredoc means the output is filtered or
# the command is something else, so leave it alone.
case "$cmd" in
  *'|'*|*'&&'*|*';'*|*'<<'*|*'>'*|*$'\n'*) exit 0 ;;
esac

# shellcheck disable=SC2086
set -- $cmd
case "${1:-}" in
  cat|less|more) ;;
  *) exit 0 ;;
esac
shift

file=""
for w in "$@"; do
  case "$w" in
    -*) ;;
    *) [ -z "$file" ] || exit 0; file=$w ;;   # more than one file: not our case
  esac
done
[ -n "$file" ] || exit 0

file=${file#\"}; file=${file%\"}; file=${file#\'}; file=${file%\'}
case "$file" in
  /*) path=$file ;;
  "~/"*) path="$HOME/${file#\~/}" ;;
  *) path="$cwd/$file" ;;
esac
[ -f "$path" ] || exit 0

n=$(wc -l < "$path" 2>/dev/null | tr -d ' ')
[ "${n:-0}" -gt 300 ] || exit 0

jq -nc --arg r "File has $n lines: use Read with offset/limit or grep -n" \
  '{hookSpecificOutput:{hookEventName:"PreToolUse",
                        permissionDecision:"deny",
                        permissionDecisionReason:$r}}'
exit 0
