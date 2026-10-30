#!/usr/bin/env bash
# One background session per issue, watched by this script instead of by the owner.
#
#   supervisor.sh launch <issue> <task>   start `claude --bg -n "#<issue>"`, at most $SUPERVISOR_SLOTS at once
#   supervisor.sh answer <issue> <text>   hand the owner's answer to the session of that issue
#   supervisor.sh tick                    delete sessions whose pull requests are all merged, then print the digest
#   supervisor.sh digest                  only print the digest
#
# Everything is read from `claude agents --json --all` and each session's
# ~/.claude/jobs/<id>/state.json (state, last result line, pull requests it opened).
set -uo pipefail

SLOTS=${SUPERVISOR_SLOTS:-3}
REPO=$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null | sed 's#/\.git$##')
JOBS=$HOME/.claude/jobs

sessions() { claude agents --json --all | jq -c '.[] | select(.kind == "background")'; }

job() { # job <id> <jq filter> -> field of the session's state.json
  [ -r "$JOBS/$1/state.json" ] && jq -r "$2" "$JOBS/$1/state.json"
}

session_of() { # session_of <issue> -> the session JSON named #<issue>, newest first
  sessions | jq -sc --arg n "#$1" 'map(select(.name == $n)) | sort_by(-.startedAt) | .[0] // empty'
}

# One `gh pr list` per run instead of a call per pull request.
PRS=$(gh pr list -R the-kind-robots/learning-app --state all --limit 300 --json number,state 2>/dev/null || echo '[]')
pr_state() { jq -r --argjson n "$1" 'map(select(.number == $n))[0].state // "UNKNOWN"' <<<"$PRS"; }

worktree_of() { # the transcript path names the worktree the session ran in
  job "$1" '.linkScanPath // ""' | sed -nE 's#.*-claude-worktrees-([^/]+)/.*#\1#p'
}

launch() {
  local issue=$1 task=$2 running
  [ -n "$(session_of "$issue")" ] && { echo "#$issue already has a session: supervisor.sh answer $issue ..."; exit 1; }
  running=$(sessions | jq -s '[.[] | select(.state != "done" and (.name // "" | startswith("#")))] | length')
  [ "$running" -lt "$SLOTS" ] || { echo "all $SLOTS slots busy; #$issue waits"; exit 1; }
  claude --bg -n "#$issue" "Issue #$issue. $task"
}

answer() {
  local issue=$1 text=$2 s id
  s=$(session_of "$issue")
  [ -n "$s" ] || { echo "no session named #$issue"; exit 1; }
  id=$(jq -r .id <<<"$s")
  # A running session would be copied by --resume, so it stops first; its conversation is kept.
  [ "$(jq -r .state <<<"$s")" = done ] || claude stop "$id" >/dev/null
  claude --bg --resume "$(jq -r .sessionId <<<"$s")" "$text"
}

cleanup() { # done sessions whose pull requests are all merged
  local s id prs pr wt out
  sessions | jq -c 'select(.state == "done")' | while IFS= read -r s; do
    id=$(jq -r .id <<<"$s")
    prs=$(job "$id" '[(.children // [])[] | select(.kind == "pr") | .id] | join(" ")')
    [ -n "$prs" ] || continue
    for pr in $prs; do [ "$(pr_state "$pr")" = MERGED ] || continue 2; done
    wt=$(worktree_of "$id")
    if [ -n "$wt" ] && [ -d "$REPO/.claude/worktrees/$wt" ]; then
      git -C "$REPO/.claude/worktrees/$wt" fetch -q origin
      git -C "$REPO/.claude/worktrees/$wt" switch -q --detach origin/master
    fi
    if out=$(claude rm "$id" 2>&1); then
      echo "- удалена $(jq -r '.name // .id' <<<"$s") (PR $prs влиты)"
    else
      echo "- НЕ удалена $(jq -r '.name // .id' <<<"$s"): $out"
    fi
  done
}

digest() {
  local s id name state detail prs open pr
  sessions | while IFS= read -r s; do
    id=$(jq -r .id <<<"$s"); name=$(jq -r '.name // "(без имени)"' <<<"$s")
    # A live session waiting for an answer is "idle" in `claude agents`, "blocked" in its job state.
    state=$(job "$id" '.state // empty'); state=${state:-$(jq -r .state <<<"$s")}
    detail=$(job "$id" '.detail // "" | gsub("\\s+"; " ") | .[0:160]')
    prs=$(job "$id" '[(.children // [])[] | select(.kind == "pr") | .id] | join(" ")')
    open=""
    for pr in $prs; do [ "$(pr_state "$pr")" = MERGED ] || open="$open #$pr"; done
    case "$state" in
      blocked) echo "1|ждёт вас|$name|$id|$detail" ;;
      done) if [ -n "$open" ]; then echo "2|PR на проверку$open|$name|$id|$detail"
            elif [ -n "$prs" ]; then echo "5|влито, удалится на tick|$name|$id|$detail"
            else echo "4|готово без PR|$name|$id|$detail"; fi ;;
      *) echo "3|в работе|$name|$id|$detail" ;;
    esac
  done | sort -t'|' -k1,1 | awk -F'|' '{ printf "- %s · %s (%s) — %s\n", $2, $3, $4, $5 }'
}

case "${1:-}" in
  launch) [ $# -eq 3 ] || { echo "usage: $0 launch <issue> <task>"; exit 2; }; launch "$2" "$3" ;;
  answer) [ $# -eq 3 ] || { echo "usage: $0 answer <issue> <text>"; exit 2; }; answer "$2" "$3" ;;
  tick) cleanup; digest ;;
  digest) digest ;;
  *) sed -n '2,11p' "$0"; exit 2 ;;
esac
