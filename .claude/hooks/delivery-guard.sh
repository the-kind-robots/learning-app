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
      case "$branch" in
        [0-9]*-*) ;;  # issue-linked branch from `gh issue develop` — allowed
        *)
          decide deny "Refused: HEAD is '${branch:-unknown}', which carries no issue number, so this pull request would have no issue and no board entry behind it.

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
