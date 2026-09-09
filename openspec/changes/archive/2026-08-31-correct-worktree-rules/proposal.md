## Why

`AGENTS.md`, **# Branches and Worktrees**, describes a harness that has since changed
(measurements there date from 2026-08-15/16, Claude Code 2.1.241 and earlier). One bullet is
now false in the direction that costs a wasted cycle: it tells an agent launched from the
main checkout to create and enter its own worktree, which the harness refuses outright on
2.1.251. The section also spends five bullets on what is one invariant. Issue #382.

## What Changes

- Rewrite `AGENTS.md` **# Branches and Worktrees** around one rule — everything under your
  own worktree root is yours, nothing outside it is — and cut the narration the harness no
  longer supports. Re-date the measurement note to 2026-08-31 / Claude Code 2.1.251.
- Replace the false "agent launched from the main checkout works in a directory of its own"
  bullet with the way work is handed out now: spawn the executor with `isolation: "worktree"`.
- Drop the "no repo setting relaxes it" claim; the harness refusal itself names
  `worktree.bgIsolation` as the owner's lever.
- Correct the same stale claims in `.claude/rules/repo-delivery.md`, keeping it as short.
- Correct the stale 2026-08-16 paragraph in the `.claude/hooks/coordinator-guard.sh` header
  comment: a write into an agent's own nested worktree is no longer refused, while the
  repository-wide refusal for an unpinned agent stands. Comment text only — no logic change.

Not changed: hook logic, settings, `.gitignore`.

## Capabilities

### New Capabilities

None. No product behaviour moves: this change edits agent process documentation and a shell
comment.

### Modified Capabilities

- `repo-delivery-workflow`: the requirement *A branch for tracked work is linked to its issue*
  carries the stale harness claim in two of its scenarios — that a delegated agent is
  "launched without worktree isolation, because a pinned agent cannot reach a worktree
  created beside its own", and that a coordinator's agent "can create its own
  `.claude/worktrees/<slug>`". Both are false on 2.1.251. The requirement is restated around
  the invariant and the delegation scenario is corrected to `isolation: "worktree"`.

`repo-agent-infrastructure` (*Worktrees live where the agent tool puts them*) was checked and
needs no delta: it never claimed an unpinned agent could enter a worktree, and the nested
`wt-<n>` exception it records still holds.

## Impact

- `AGENTS.md` (# Branches and Worktrees; the `coordinator-guard.sh` sentence at line 20 if
  the rewrite moves what it points at)
- `.claude/rules/repo-delivery.md`
- `.claude/hooks/coordinator-guard.sh` — header comment only; `bash -n` must pass and the
  diff must contain no executable line.

No source code, no tests, no deployment surface.
