## Why

The repo's worktree rule — `gh issue develop <n>` then `git worktree add .claude/worktrees/<slug>`
then `EnterWorktree` — cannot be executed by an agent that was launched with worktree isolation.
The harness pins such an agent to the worktree it was given and refuses every Bash call whose
working directory resolves elsewhere, `git -C <path>` included. `EnterWorktree` moves file access
into the new worktree but not the guard, and `ExitWorktree` is unavailable to a pinned agent, so the
move cannot even be undone. Three branches from one afternoon's work ended up nested at
`.claude/worktrees/331-issue-flow-cost/wt-*` because that was the only sequence that ran at all.

The rule describes one sequence for two situations that behave differently. The fix is in the rule
and in how agents are launched, not in a commit: the guard belongs to the harness and cannot be
turned off from this repository.

## What Changes

- The rule states the launch decision first: an agent expected to land a specific issue branch is
  launched **without** worktree isolation. Isolation fits work that stays on the branch the agent
  was handed.
- For an agent that is already isolated and cannot relaunch itself, the rule records the one
  sequence that runs end to end: `gh issue develop <n>` without `--checkout`, `git worktree add` a
  linked worktree **inside** its own worktree, and plain `cd` in Bash. `EnterWorktree` is
  specifically excluded, with the reason.
- `.gitignore` gains the nested worktree path. `git add -A` in the parent worktree stages a nested
  worktree as an embedded repository, which a finish script would happily commit as a bogus gitlink.
- Closeout covers the nested case: the inner worktree is removed with the branch.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `repo-agent-infrastructure`: the "worktrees live where the agent tool puts them" requirement gains
  the isolated-agent exception and the ignore rule it requires.
- `repo-delivery-workflow`: the branch/worktree ordering requirement records both situations — a
  session that can create worktrees freely, and an agent pinned to one.

## Impact

- `AGENTS.md` ("Branches and Worktrees")
- `.claude/rules/repo-delivery.md`
- `.gitignore`
- No runtime code. The harness guard itself is out of reach of this repository; the change makes the
  rule match it.
