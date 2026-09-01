#!/usr/bin/env bash
# PreToolUse(Bash). The delivery flow is not optional here — see AGENTS.md, "# Delivery".
#
# Refuses the commands that create work GitHub can see but the project board cannot.
# The gh-project-workflow scripts pass untouched by construction: this hook sees only
# the command string the agent itself submitted, never the `gh` calls a script makes in
# a subprocess. Running the wrapper is the escape hatch, and it needs no marker.
#
# Gate on the invariant ("an issue exists for this work"), not on the verb: `gh pr create`
# is correct from an issue branch and wrong from master, and the delivery skill itself
# prescribes the manual form when the tree is dirty.
set -uo pipefail
set -f  # no globbing when statements are word-split below

decide() { # decide <allow|deny|ask> <reason>
  jq -nc --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",
                          permissionDecision:$d,
                          permissionDecisionReason:$r}}'
  exit 0
}

command -v jq >/dev/null 2>&1 || exit 0

# `gh pr create` may name its source branch explicitly. Judge the branch the command
# actually pushes from, not the one this hook's own working directory happens to be on:
# the coordinating session and the agents it raises sit in the main checkout on `master`,
# and under the coordinator rule that is the NORMAL configuration, so a HEAD-only test
# refuses every correct invocation. Found while trying to open the pull request for #346.
#
# The invariant is unchanged — the branch must still look like `<number>-<slug>`. Only the
# source of the name moves.
#
# Words arrive already split (heredoc bodies stripped, `set -f` so no globbing), so quoting
# is gone by this point: a `--head` inside a quoted title would look like a flag. A candidate
# carrying a quote character is therefore not treated as a branch name, which covers the
# common prose case and nothing more.
head_branch() { # head_branch <words...> -> branch named by --head/-H, empty when absent
  local w cand
  while [ "$#" -gt 0 ]; do
    w=$1; shift
    case "$w" in
      --head=*) cand=${w#--head=} ;;
      -H=*)     cand=${w#-H=} ;;
      --head|-H) cand=${1:-} ;;
      *) continue ;;
    esac
    case "$cand" in ''|-*|*[\"\']*) continue ;; esac
    printf '%s' "${cand#*:}"   # a fork's head is `owner:branch`
    return
  done
}

cmd=$(jq -r '.tool_input.command // empty' 2>/dev/null || true)
[ -n "$cmd" ] || exit 0

START='bash .skills/gh-project-workflow/scripts/start_issue_flow.sh'
FINISH='bash .skills/gh-project-workflow/scripts/finish_issue_flow.sh'

# Drop heredoc bodies. The opener line is kept — it carries the real command — and
# everything up to the terminator is skipped, so prose inside an issue or PR body can
# never be mistaken for a command.
stripped=$(printf '%s\n' "$cmd" | awk '
  skip { if ($0 ~ term) skip = 0; next }
  { print
    if (match($0, /<<-?[ \t]*[\047"]?[A-Za-z_][A-Za-z0-9_]*[\047"]?/)) {
      d = substr($0, RSTART, RLENGTH)
      sub(/^<<-?[ \t]*/, "", d); gsub(/[\047"]/, "", d)
      term = "^[ \t]*" d "[ \t]*$"; skip = 1 } }')

# One statement per line, so only statement heads are ever inspected.
statements=$(printf '%s\n' "$stripped" | sed -E 's/(&&|\|\||[;|&])/\n/g')

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)

while IFS= read -r st; do
  case "$st" in
    *DELIVERY_GUARD=off*)
      decide ask "DELIVERY_GUARD=off is set: the delivery guard is being bypassed on purpose. Approve only if you asked for this raw command." ;;
  esac

  # Strip leading environment assignments and benign wrappers.
  st=$(printf '%s' "$st" | sed -E 's/^[[:space:]]+//
    :a
    s/^[A-Za-z_][A-Za-z0-9_]*=("[^"]*"|'\''[^'\'']*'\''|[^[:space:]]*)[[:space:]]+//
    ta
    s/^(sudo|env|command|nohup|time|exec)[[:space:]]+//')

  # shellcheck disable=SC2086
  set -- $st
  head=${1:-}; sub=${2:-}; verb=${3:-}

  # Reading a command's documentation is not running it: `--help` prints text and creates no
  # issue, no pull request and no branch, so neither a deny nor an ask has anything to
  # protect. Matched as a whole word, and only when the statement carries no quote character:
  # words arrive already split, so a `--help` inside a quoted title is indistinguishable from
  # a real flag — the same blind spot `head_branch` documents above. Quotes present, the
  # statement is judged exactly as before.
  case "$st" in
    *[\"\']*) ;;
    *) for w in "$@"; do
         case "$w" in --help|-h) continue 2 ;; esac
       done ;;
  esac

  case "$head" in
    git)
      case "$sub $verb" in
        "checkout -b"|"switch -c")
          decide ask "AGENTS.md, Branches: a tracked branch comes from \`gh issue develop <n> --checkout\`, so GitHub links it to its issue. A branch made this way leaves the issue with no development link and drops out of every cleanup. Approve only for throwaway local work." ;;
      esac
      continue ;;
    gh|*/gh) ;;
    *) continue ;;
  esac

  case "$sub $verb" in
    "issue create")
      decide deny "Refused: a raw \`gh issue create\` produces an issue that is on no board, with no Status and no Priority. #288, #289, #290 and #292 were lost that way.

Use the flow — invoke the \`repo-task-delivery\` skill, or run its GitHub step directly:
  $START --title \"...\" --body-file <path> --priority <Blocker|Critical|Major|Minor|Trivial> --status \"In progress\" --base master

That one call creates the issue, reuses an existing item of the same title, puts it on project 'Learning app' (2), sets the fields, and runs \`gh issue develop --checkout\`.

If the owner asked for the raw command, re-run it prefixed with DELIVERY_GUARD=off." ;;

    "pr create")
      named=$(head_branch "$@")
      source_branch=${named:-$branch}
      if [ -n "$named" ]; then
        where="--head names '$named'"
      else
        where="HEAD is '${branch:-unknown}'"
      fi
      case "$source_branch" in
        [0-9]*-*) ;;  # issue-linked branch from `gh issue develop` — allowed
        *)
          decide deny "Refused: $where, which carries no issue number, so this pull request would have no issue and no board entry behind it.

Get the issue and its branch first:
  $START --title \"...\" --priority Major --status \"In progress\" --base master
or, when the issue already exists:
  gh issue develop <n> -R u473t8/learning-app --checkout --base master

From a <number>-<slug> branch \`gh pr create\` is allowed. To commit, push, open the PR and set Status in one step:
  $FINISH --issue <n> --base master
(with a dirty tree prefer manual git/gh steps — the finish script runs \`git add -A\`).

If the owner asked for the raw command, re-run it prefixed with DELIVERY_GUARD=off." ;;
      esac ;;
  esac
done <<EOF
$statements
EOF

exit 0
