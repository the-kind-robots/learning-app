#!/usr/bin/env bash
# PreToolUse(Edit|Write|NotebookEdit) and PreToolUse(Bash). Keeps the coordinating session
# out of the editor — see AGENTS.md, "# Delivery" and "# Branches and Worktrees".
#
# The rule: the coordinating session files work and hands it to an executor; the executor
# does the editing, on an issue branch, in its own worktree. Nothing enforced that, and the
# rule exists because it was broken — the coordinator edited a script itself, entered a
# worktree to do it, and every agent raised from there inherited that pin (#346, #348).
#
# What this file is, and what it is not
# -------------------------------------
# It covers the editor tools with a REFUSAL and the common Bash write shapes with a PROMPT.
# The list of Bash write shapes below is knowingly incomplete, and it cannot be completed:
# a shell command can reach a file in unbounded ways. Anything not on the list reaches the
# repository unchallenged. The guarantee is NOT this hook — it is the stop-and-report rule
# in AGENTS.md, "# Delivery": a refusal ends that line of work and goes to the owner
# verbatim. The hook is the tripwire that makes a lapse visible, not a lock. Do not close
# this file believing the repository is sealed; it is not, and it will not become so.
#
# Why Bash prompts where Edit refuses
# -----------------------------------
# THE TARGET OF AN EDIT IS KNOWN AND THE TARGET OF A SHELL COMMAND IS A GUESS: `file_path`
# and `notebook_path` arrive in the payload, so a wrong refusal there is impossible, while a
# Bash target is parsed out of a string and a hard refusal would fire on the compound
# commands people legitimately run all day. Certainty earns `deny`; a guess earns `ask`.
#
# Bash is covered because the uncovered route was used, three times, while this very change
# was being built: an agent blocked on Edit/Write applied its edits through Bash instead and
# said so; a commit was assembled with low-level git and pushed straight to a ref; and a `cp`
# over a hook script was attempted after a denial. None of that is about who did it — it is
# what an unguarded route next to a guarded one attracts.
#
# On the Bash matcher this hook emits `ask` or NOTHING. Never `allow`, never `deny`.
# `delivery-guard.sh` shares that event and denies `gh issue create` and `gh pr create` for
# reasons of its own; an explicit `allow` from here could undercut its refusal. Silence
# defers — the same discipline used for the allow cases below.
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
# Because that assumption is unverified, the editor path does not rest on it alone. It also
# reads the branch of the worktree that owns the TARGET FILE — not the session's cwd, which
# says nothing about what is being written. That is the same invariant `delivery-guard.sh`
# applies to `gh pr create`: a `<number>-<slug>` HEAD came from `gh issue develop`, anything
# else did not. The two combine so a wrong assumption costs a prompt, never a wall:
#
#   Edit / Write / NotebookEdit
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
#   Bash
#   subagent (agent_id present)                 -> silent. Executors are the normal caller.
#   write-shaped statement, target in the repo  -> ask.
#   anything else                               -> silent. /tmp, /dev/null, $HOME, reads,
#                                                         and every write shape not listed.
#
# If `agent_id` turns out to be absent for subagents as well, the worst case is that
# executors meet an approval prompt on issue-branch edits instead of passing silently —
# visible and diagnosable within one session, not a blocked repository.
#
# The harness has a write guard of its own, and it does not agree with this one. Measured
# 2026-08-31 on Claude Code 2.1.251. For a subagent whose parent session has not isolated it
# keys on that PARENT, not on the caller and not on the target: Edit/Write/NotebookEdit are
# refused repository-wide with "this subagent's parent bg session hasn't isolated yet, so
# writes to the shared checkout are blocked", including for a write aimed at a worktree the
# agent had just created for itself. For an agent that IS pinned to a worktree the boundary
# is a path prefix on its own root — a nested `wt-<n>` worktree it adds there is writable
# through the editor tools, which it was not when this was last measured on 2026-08-16,
# while a sibling worktree and the shared checkout stay out of reach. Bash is outside the
# harness guard entirely: `git worktree add` wrote 731 files in the same session that could
# not write one file through Edit. So an allow from this hook is not a promise the write
# will land, and a refusal the reader sees may be the harness's rather than this one's.
# Check the wording. Since 2.1.143 `worktree.bgIsolation` refuses a BACKGROUND coordinator's
# editor writes natively, so this hook's unique coverage is the interactive session and the
# shell writes; an interactive coordinator is held by this hook alone.
#
# This hook warns and leaves a trace. It reads a payload, a command string and a branch name,
# so it stops an accident, not an intent. Do not read a refusal here as an obstacle to get
# around — AGENTS.md, "# Delivery": stop, and report the action and the refusal verbatim to
# the owner.
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

input=$(cat)
[ -n "$input" ] || exit 0

# A subagent is the executor. Nothing below applies to it.
agent_id=$(printf '%s' "$input" | jq -r '.agent_id // empty' 2>/dev/null || true)
[ -z "$agent_id" ] || exit 0

# "Inside the repository" means "in some worktree of THIS repository", asked of git rather
# than derived from this script's path. The settings entry resolves the hook out of
# CLAUDE_PROJECT_DIR, so the main checkout's copy runs even for an agent sitting in a
# worktree; a path-derived root would then guard only the main checkout and wave every
# worktree through. Comparing the common git dir covers main checkout and worktrees alike,
# whichever copy of this script is the one executing.
self=$(readlink -f -- "$0" 2>/dev/null || printf '%s' "$0")
here=$(dirname -- "$self")
own_repo=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
[ -n "$own_repo" ] || exit 0                # cannot place ourselves — allow

# repo_dir <absolute path> -> the nearest existing ancestor directory, when that directory
# belongs to THIS repository. Non-zero otherwise: not under git, or a different repository.
repo_dir() {
  local abs dir target_repo
  abs=$1
  dir=$(dirname -- "$abs")
  while [ ! -d "$dir" ] && [ "$dir" != "/" ]; do dir=$(dirname -- "$dir"); done
  target_repo=$(git -C "$dir" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)
  [ -n "$target_repo" ] || return 1
  [ "$target_repo" = "$own_repo" ] || return 1
  printf '%s' "$dir"
}

tool=$(printf '%s' "$input" | jq -r '.tool_name // empty' 2>/dev/null || true)

START='bash .skills/gh-project-workflow/scripts/start_issue_flow.sh'

# ---------------------------------------------------------------- Bash: ask, or say nothing
if [ "$tool" = "Bash" ]; then
  cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null || true)
  [ -n "$cmd" ] || exit 0
  cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null || true)
  [ -n "$cwd" ] || cwd=$PWD

  # Same heredoc handling as delivery-guard.sh: keep the opener line, drop the body, so the
  # prose of an issue or a pull request body is never parsed as a command. The owner writes
  # `cat > /tmp/....md <<'EOF'` constantly in this role.
  stripped=$(printf '%s\n' "$cmd" | awk '
    skip { if ($0 ~ term) skip = 0; next }
    { print
      if (match($0, /<<-?[ \t]*[\047"]?[A-Za-z_][A-Za-z0-9_]*[\047"]?/)) {
        d = substr($0, RSTART, RLENGTH)
        sub(/^<<-?[ \t]*/, "", d); gsub(/[\047"]/, "", d)
        term = "^[ \t]*" d "[ \t]*$"; skip = 1 } }')

  # One statement per line, so only statement heads are ever inspected.
  statements=$(printf '%s\n' "$stripped" | sed -E 's/(&&|\|\||[;|&])/\n/g')

  # candidates <words...> -> one possible write target per line. Deliberately short and
  # deliberately incomplete; see the header. A word carrying a quote character is not a path
  # here — quoting is already gone at this point, so such a word is prose, not a filename.
  candidates() {
    local w expect=0 head sub last

    # Output redirection, on any statement: `> path`, `>>path`, `2> path`.
    for w in "$@"; do
      if [ "$expect" = 1 ]; then expect=0; printf '%s\n' "$w"; continue; fi
      case "$w" in
        '>'|'>>'|'&>'|'&>>'|[0-9]'>'|[0-9]'>>') expect=1 ;;
        '>'*|[0-9]'>'*|'&>'*)                   printf '%s\n' "${w##*>}" ;;
      esac
    done

    head=${1:-}; sub=${2:-}
    case "$head" in
      tee|truncate|patch)
        shift || true
        for w in "$@"; do case "$w" in -*) ;; *) printf '%s\n' "$w" ;; esac; done ;;
      cp|mv|install|ln)
        # The destination is the last operand; the others are read, not written.
        last=""
        for w in "$@"; do case "$w" in -*) ;; *) last=$w ;; esac; done
        [ -z "$last" ] || printf '%s\n' "$last" ;;
      sed)
        case " $* " in
          *" -i "*|*" -i"*)
            shift || true
            for w in "$@"; do case "$w" in -*) ;; *) printf '%s\n' "$w" ;; esac; done ;;
        esac ;;
      dd)
        for w in "$@"; do case "$w" in of=*) printf '%s\n' "${w#of=}" ;; esac; done ;;
      git)
        case "$sub" in
          restore)
            shift 2 || true
            for w in "$@"; do case "$w" in -*) ;; *) printf '%s\n' "$w" ;; esac; done ;;
          checkout)
            # Only the path form. `git checkout <branch>` moves HEAD, it does not write a
            # file, and treating its argument as a path would prompt on every branch switch.
            expect=0
            for w in "$@"; do
              if [ "$expect" = 1 ]; then printf '%s\n' "$w"; continue; fi
              [ "$w" = "--" ] && expect=1
            done ;;
        esac ;;
    esac
  }

  while IFS= read -r st; do
    [ -n "$st" ] || continue

    # Strip leading environment assignments and benign wrappers, as delivery-guard.sh does.
    st=$(printf '%s' "$st" | sed -E 's/^[[:space:]]+//
      :a
      s/^[A-Za-z_][A-Za-z0-9_]*=("[^"]*"|'\''[^'\'']*'\''|[^[:space:]]*)[[:space:]]+//
      ta
      s/^(sudo|env|command|nohup|time|exec)[[:space:]]+//')

    # shellcheck disable=SC2086
    set -- $st
    [ "$#" -gt 0 ] || continue

    while IFS= read -r cand; do
      [ -n "$cand" ] || continue
      case "$cand" in
        *[\"\']*) continue ;;                  # prose, not a path
        ''|*[!0-9]*) ;;
        *) continue ;;                         # a bare number is a flag's argument
      esac                                     # (`truncate -s 0`, `install -m 755`)
      case "$cand" in
        /dev/*) continue ;;
      esac
      case "$cand" in
        /*) abs=$cand ;;
        *)  abs=$cwd/$cand ;;
      esac
      abs=$(readlink -m -- "$abs" 2>/dev/null || printf '%s' "$abs")
      case "$abs" in
        */.claude/settings.local.json) continue ;;   # untracked local override
      esac
      repo_dir "$abs" >/dev/null || continue

      decide ask "This session carries no subagent id, so it looks like the coordinating session, and this command writes to '$abs', inside the repository.

The coordinator files work and hands it to an executor; it does not edit the repository itself (AGENTS.md, # Delivery). A shell write is the same edit an Edit/Write refusal would have caught, taken by a route the editor matcher never sees.

If you are here because an edit was just refused: stop, and report the action and the refusal verbatim to the owner — do not complete it through the shell. Otherwise hand the edit to an executor, which works on an issue branch in its own worktree:
  $START --title \"...\" --priority <Blocker|Critical|Major|Minor|Trivial> --status \"In progress\" --base master

This check reads a command string, so it guesses. Approve it if the guess is wrong."
    done <<CANDIDATES
$(candidates "$@")
CANDIDATES
  done <<STATEMENTS
$statements
STATEMENTS

  exit 0
fi

# ------------------------------------------- Edit / Write / NotebookEdit: refuse, or ask
target=$(printf '%s' "$input" \
  | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' 2>/dev/null || true)
[ -n "$target" ] || exit 0

# Write targets a file that need not exist yet, so resolve without requiring it.
abs=$(readlink -m -- "$target" 2>/dev/null || printf '%s' "$target")

case "$abs" in
  */.claude/settings.local.json) exit 0 ;;  # untracked local override, deliberately open
esac

dir=$(repo_dir "$abs") || exit 0            # not this repository — scratch, /tmp, $HOME

# The branch of the worktree that owns the file, not the one the session happens to sit on.
branch=$(git -C "$dir" rev-parse --abbrev-ref HEAD 2>/dev/null || true)
[ -n "$branch" ] || exit 0                  # git could not answer — allow, never deny on our own error

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
