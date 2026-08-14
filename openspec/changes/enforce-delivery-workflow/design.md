# Design: enforce-delivery-workflow

## What actually failed

`repo-task-delivery`'s description is already as assertive as a description can be, and
its body already contains the exact anti-pattern that was committed — *"Do not wait for
the user to explicitly say create an issue"*. It still did not fire.

That is the evidence that decides the design: **description-driven skill selection cannot
be the enforcement layer.** Context makes an agent want to comply; only a hook makes
compliance necessary. So the layers split by job — context for correctness, the hook for
enforcement.

## Three channels, deliberately redundant

1. `@AGENTS.md` in `CLAUDE.md` — the root cause fix. A Markdown link is documentation; an
   import is loaded before the first turn. This does not violate "keep `CLAUDE.md` thin":
   the knowledge still lives in `AGENTS.md`, a normal repo file Codex reads natively, and
   one import line adds no knowledge to `CLAUDE.md` — it repairs a link that was
   decorative.
2. `.claude/rules/repo-delivery.md` with no `paths:` key — the harness loads such a file
   unconditionally at session start, at the same priority as `CLAUDE.md`. Survives
   `CLAUDE.md` drift.
3. The `PreToolUse` hook — what remains when context has been compacted away or reasoned
   around mid-session.

`paths: ["**"]` is deliberately not used as an always-on trick. Path-scoped rules surface
when a matching file is *edited*, and by then the agent has already decided to skip the
flow — the issue was supposed to exist before the first edit.

## Gate on the invariant, not the verb

The invariant that was violated is "there is an issue for this work". So:

| Command | Decision |
|---|---|
| `gh issue create` | **deny** — the one command that manufactures untracked work from nothing |
| `gh pr create` from `<number>-<slug>` | allow — the branch came from `gh issue develop`, so the issue exists |
| `gh pr create` from any other branch | **deny** — a PR with no issue behind it is untracked work by definition |
| `git checkout -b`, `git switch -c` | **ask** — `AGENTS.md` forbids it *for tracked work*; throwaway branches are legitimate and a human can tell the difference in one keystroke |
| `gh project item-add` / `item-edit` | allow |
| everything else | allow |

Two of these need justifying.

**Why `gh pr create` is not denied outright.** `repo-task-delivery`'s Dirty Worktree
Guardrail explicitly prescribes *"prefer manual `git add` / `git commit` / `gh pr create`
/ `gh pr merge` over all-in-one wrappers when the tree is dirty"* — and
`finish_issue_flow.sh` does `git add -A`, so with this repo's habitually dirty tree that
guidance is the normal path, not an edge case. A blanket deny would forbid what the repo's
own skill tells you to do. Gating on the branch keeps both rules true at once.

**Why `gh project item-add` stays allowed.** It is sanctioned by the skill's step 2, and
it is the repair tool for exactly this incident — putting #288, #290 and #292 back on the
board needs it. Blocking the remedy for the disease is backwards.

## The escape hatch is free

`start_issue_flow.sh` builds its argv as an array — `issue_args=(issue create -R "$repo"
…)` — and invokes `gh "${issue_args[@]}"` in a subprocess. A `PreToolUse` hook sees only
the command string the agent itself submitted. So running the wrapper is inherently
unblocked, and no marker, allowlist entry or content scan is needed. Even a naive scan of
the script's text would not trip, because the literal string `gh issue create` does not
appear in it.

For the case where the owner asks for a raw command outright, a `DELIVERY_GUARD=off`
prefix downgrades the deny to `ask` rather than to a silent allow — the human still
confirms in the permission prompt, so a self-serving agent gains nothing by reaching for
it.

## Denials have to be recoverable

A bare "permission denied" produces flailing. Every deny message names the exact
replacement command with its required flags, plus the alternative for the
issue-already-exists case. Two standing rules for these messages: never deny without
naming the replacement, and never name a path that does not resolve in a worktree — which
is why the `.codex/` fix is a hard dependency of this change rather than a nice-to-have.

## The risk that could void all of it

`.claude/settings.json` allows `Bash(gh *)`, which is why `gh issue create` ran with no
prompt at all during the incident. Hooks are documented to run before the permission
decision and to deny regardless of allow-rules, but that has to be proven here rather than
assumed. If it turns out allow-rules short-circuit hooks, the fallback is to drop
`Bash(gh *)` and enumerate the read-only subcommands so writes prompt — worth doing as
hardening in any case.

## Branch rule versus EnterWorktree

`AGENTS.md` requires branches only from `gh issue develop --checkout` and worktrees only
from `EnterWorktree`. The built-in mechanism creates its own branch from master, so any
worktree session violates the branch rule by default and leaves the issue with no
development link — which is exactly what happened on #289, and what background jobs are
steered into, since they are told to enter a worktree before their first edit.

`EnterWorktree` accepts the path of an existing worktree, so the order that satisfies both
rules is: create the branch with `gh issue develop`, add a worktree on it under
`.claude/worktrees/`, then enter that path. Recorded in `AGENTS.md` rather than left for
each session to rediscover.
