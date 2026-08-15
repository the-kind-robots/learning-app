#!/usr/bin/env bash
# PreToolUse(Edit|Write|NotebookEdit). Keeps the coordinating session out of the editor —
# see AGENTS.md, "# Delivery" and "# Branches and Worktrees".
#
# The rule: the coordinating session files work and hands it to an executor; the executor
# does the editing, on an issue branch, in its own worktree. Nothing enforced that, and the
# rule exists because it was broken — the coordinator edited a script itself, entered a
# worktree to do it, and every agent raised from there inherited that pin (#346, #348).
#
# What this hook can tell apart, and what it cannot
# -------------------------------------------------
# `agent_id` appears in the PreToolUse payload only for subagents, so its ABSENCE is the
# coordinator marker. That comes from the hooks documentation, NOT from a measurement here.
# Capturing a real payload needs a writable hook in the main checkout, and both routes to
# one are closed from inside a session: the harness refuses Edit/Write against the shared
# checkout from an isolated agent, and overwriting a hook script through Bash is refused
# too. So the marker is documented, not observed. The next reader should treat it that way.
#
# Because that assumption is unverified, the hook does not rest on it alone. It also reads
# the branch of the worktree that owns the TARGET FILE — not the session's cwd, which says
# nothing about what is being written. That is the same invariant `delivery-guard.sh`
# already applies to `gh pr create`: a `<number>-<slug>` HEAD came from `gh issue develop`,
# anything else did not. The two combine so a wrong assumption costs a prompt, never a wall:
#
#   subagent (agent_id present)                 -> allow. Never stand in the executor's way.
#   no agent_id, target outside the repo        -> allow. Scratch, /tmp, $HOME.
#   no agent_id, any .claude/settings.local.json -> allow. Untracked, local-only.
#   no agent_id, in repo, HEAD not an issue br. -> deny.  The coordinator editing master.
#   no agent_id, in repo, HEAD is an issue br.  -> ask.   Either the coordinator wandered
#                                                         into a worktree — the exact failure
#                                                         this exists to catch — or a main
#                                                         session doing full-stand work on
#                                                         its own issue branch, which
#                                                         AGENTS.md explicitly allows. The
#                                                         hook cannot separate those; the
#                                                         human can.
#
# If `agent_id` turns out to be absent for subagents as well, the worst case is that
# executors meet an approval prompt on issue-branch edits instead of passing silently —
# visible and diagnosable within one session, not a blocked repository.
#
# What it does not cover: Bash. `printf > file`, `sed -i` and `cp` reach the same files and
# this matcher never sees them. Measured while building this: a Bash write into the shared
# checkout succeeds where Edit/Write are refused. The hook narrows a habit; it is not a seal.
#
# The harness has a write guard of its own, and it does not agree with this one. Measured
# 2026-08-16: it keys on the PARENT session, not on the caller and not on the target —
# Edit/Write/NotebookEdit were refused repository-wide with "this subagent's parent bg
# session hasn't isolated yet, so writes to the shared checkout are blocked", including for
# a write aimed at a worktree the agent had just created for itself. Bash is outside it
# entirely: `git worktree add` wrote 731 files in the same session that could not write one
# file through Edit. So an allow from this hook is not a promise the write will land, and a
# refusal the reader sees may be the harness's rather than this one's. Check the wording.
#
# This hook warns and leaves a trace. It locks nothing: it reads a payload and a branch name,
# so it stops an accident, not an intent, and the uncovered routes above are real. Do not
# read a refusal here as an obstacle to get around — AGENTS.md, "# Delivery": stop, and
# report the action and the refusal verbatim to the owner.
set -uo pipefail

decide() { # decide <allow|deny|ask> <reason>
  jq -nc --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",
                          permissionDecision:$d,
                          permissionDecisionReason:$r}}'
  exit 0
}

command -v jq >/dev/null 2>&1 || exit 0

input=$(cat)
[ -n "$input" ] || exit 0

# A subagent is the executor. Nothing below applies to it.
agent_id=$(printf '%s' "$input" | jq -r '.agent_id // empty' 2>/dev/null || true)
[ -z "$agent_id" ] || exit 0

target=$(printf '%s' "$input" \
  | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' 2>/dev/null || true)
[ -n "$target" ] || exit 0

# Write targets a file that need not exist yet, so resolve without requiring it.
abs=$(readlink -m -- "$target" 2>/dev/null || printf '%s' "$target")

case "$abs" in
  */.claude/settings.local.json) exit 0 ;;  # untracked local override, deliberately open
esac

# "Inside the repository" means "in some worktree of THIS repository", asked of git rather
# than derived from this script's path. The settings entry resolves the hook out of
# CLAUDE_PROJECT_DIR, so the main checkout's copy runs even for an agent sitting in a
# worktree; a path-derived root would then guard only the main checkout and wave every
# worktree through. Comparing the common git dir covers main checkout and worktrees alike,
# whichever copy of this script is the one executing.
self=$(readlink -f -- "$0" 2>/dev/null || printf '%s' "$0")
here=$(dirname -- "$self")

dir=$(dirname -- "$abs")
while [ ! -d "$dir" ] && [ "$dir" != "/" ]; do dir=$(dirname -- "$dir"); done

target_repo=$(git -C "$dir" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
[ -n "$target_repo" ] || exit 0             # not under git at all — scratch, /tmp, $HOME
own_repo=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
[ -n "$own_repo" ] || exit 0                # cannot place ourselves — allow
[ "$target_repo" = "$own_repo" ] || exit 0  # a different repository — not our business

# The branch of the worktree that owns the file, not the one the session happens to sit on.
branch=$(git -C "$dir" rev-parse --abbrev-ref HEAD 2>/dev/null || true)
[ -n "$branch" ] || exit 0                  # git could not answer — allow, never deny on our own error

START='bash .skills/gh-project-workflow/scripts/start_issue_flow.sh'

case "$branch" in
  [0-9]*-*)
    decide ask "This session carries no subagent id, so it looks like the coordinating session — and the file sits in a worktree whose HEAD is '$branch', an issue branch.

That is either the coordinator editing inside a worktree, which is the case the delivery rule exists to prevent (AGENTS.md, # Delivery), or a main session doing full-stand work on the issue it owns, which AGENTS.md allows. This hook cannot tell those apart.

Approve only if this session owns that issue. Otherwise hand the work to an executor and let it edit in its own worktree." ;;
  *)
    decide deny "Refused: this session carries no subagent id, so it is the coordinating session, and HEAD at '$dir' is '$branch' — not an issue branch.

The coordinator files work and hands it to an executor; it does not edit the repository itself (AGENTS.md, # Delivery). Editing here is how the pin in #346 happened.

File the issue, then raise an executor for it:
  $START --title \"...\" --priority <Blocker|Critical|Major|Minor|Trivial> --status \"In progress\" --base master

The executor creates its branch with \`gh issue develop <n>\` and works in its own worktree.

Scratch outside the repository is untouched by this hook, and \`.claude/settings.local.json\` stays writable." ;;
esac

exit 0
