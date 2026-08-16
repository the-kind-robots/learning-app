# Design

## Context

#346 wrote the rule; this change enforces it. The enforcement point is a `PreToolUse` hook,
because that is where the repository already refuses things — `delivery-guard.sh` denies a
raw `gh issue create` and a `gh pr create` from a branch with no issue behind it — and
because a hook is the only place a decision can be made before the write happens.

The hard part is not the refusal. It is knowing who is asking.

## What could and could not be measured

The question the issue put first: does the hook input distinguish the main session from a
subagent? The documented answer is yes — `agent_id` is present only for subagents, so its
absence marks the coordinator, and there is no environment variable carrying the same fact.

That could not be confirmed by observation here. Capturing a real payload requires a
writable hook, and the hook that actually runs is the one under `CLAUDE_PROJECT_DIR` — the
main checkout's copy, confirmed by instrumenting a worktree copy and getting no log. Both
routes to the main checkout's copy are closed from inside a session: the harness refuses
`Edit`/`Write` against the shared checkout from an isolated agent, and overwriting a hook
script through `Bash` is refused as well.

So: documented, not observed. That is written into the hook's header comment in those words,
because the next reader will otherwise assume the guard is stronger than it is.

## Decision

Do not rest the guard on the unverified signal alone. Read two things:

1. `agent_id` — present means subagent, means executor, means get out of the way.
2. the branch of the worktree that **owns the target file**, not the session's own working
   directory. The session cwd says nothing about what is being written, and the issue itself
   points out that a cwd test stops separating coordinator from executor the moment the
   coordinator enters a worktree.

| caller | target | decision |
| --- | --- | --- |
| subagent | anything | allow |
| no `agent_id` | outside the repository | allow |
| no `agent_id` | any `.claude/settings.local.json` | allow |
| no `agent_id` | in the repository, HEAD not `<number>-<slug>` | **deny** |
| no `agent_id` | in the repository, HEAD is `<number>-<slug>` | **ask** |

The last row is the one that earns its keep. It catches the coordinator that wandered into a
worktree — the exact failure #346 describes — without hard-blocking a main session doing the
full-stand work `AGENTS.md` explicitly routes to the main checkout (sync, dictionary,
migrations, schema, CouchDB, nginx). The hook cannot separate those two; a human can.

It also bounds the cost of being wrong about `agent_id`. If the marker turns out to be absent
for subagents too, executors meet an approval prompt on issue-branch edits instead of passing
silently — visible within one session and diagnosable, rather than a repository nobody can
edit. A guard that blocks real work is removed within a week; that outcome is worse than a
guard that occasionally asks.

The `<number>-<slug>` test is not a new invention. `delivery-guard.sh` already treats exactly
that shape as "this branch came from `gh issue develop`" when deciding whether `gh pr create`
may proceed.

## Rejected alternatives

**Gate on the session's working directory.** The issue asks for this as the fallback, and it
is what the hook would do if `agent_id` did not exist. Rejected as the primary test for the
reason the issue gives: it separates coordinator from executor only until the coordinator
enters a worktree, which is the failure being prevented. It survives here only as the second
signal, applied to the target's worktree rather than the session's.

**Derive the repository root from the hook script's own path.** Implemented first, and wrong.
The registration resolves the hook out of `CLAUDE_PROJECT_DIR`, so the main checkout's copy
runs even for an agent sitting in a worktree; a path-derived root then guards only the main
checkout and waves every worktree through. Caught by verification, not by reading. Replaced
by comparing `git rev-parse --git-common-dir` for the target and for the hook, which covers
the main checkout and every worktree alike, whichever copy executes.

**Deny on the hook's own error.** Rejected. A missing `jq`, a path git cannot place, an
unreadable target — each exits 0 and lets the normal permission flow proceed. The guard never
denies because it failed to decide.

## Known limits

Written into the hook's header comment, not only here:

- The matcher covers the editor tools. `Bash` is not covered and reaches the same files.
  Measured while building this: a `Bash` write into the shared checkout succeeds where
  `Edit`/`Write` are refused.
- The harness has a write guard of its own and it does not agree with this one. Measured
  2026-08-16: it keys on the **parent session**, not on the caller and not on the target —
  writes were refused repository-wide with "this subagent's parent bg session hasn't isolated
  yet", including for a write aimed at a worktree the agent had just created for itself, while
  `git worktree add` wrote 731 files in that same session. An allow from this hook is
  therefore not a promise the write will land, and a refusal the reader sees may be the
  harness's rather than this one's.
