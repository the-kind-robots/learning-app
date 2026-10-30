---
name: session-supervisor
description: "Run several issues at once in background sessions without the owner watching them: launch one `claude --bg` session per issue, collect what each is waiting for into one digest, hand the owner's answers back, and delete sessions whose pull requests are merged. Use when the interactive session starts, when the owner names tasks to run in parallel, answers a session's question, or asks what is in flight."
---

# Session Supervisor

The interactive session is the coordinator. The owner talks only to it; background sessions are read through the script, never watched by hand.

Script: `bash .claude/skills/session-supervisor/scripts/supervisor.sh <command>`

| Command | Does |
|---|---|
| `digest` | prints every background session: waiting for the owner, PRs to review, in work, finished without a PR |
| `tick` | deletes sessions whose pull requests are all merged (`claude rm`, never forced), then prints the digest |
| `launch <n> "<task>"` | starts `claude --bg -n "#<n>"`; refuses when issue `<n>` already has a session or all slots (`SUPERVISOR_SLOTS`, default 3) are busy |
| `answer <n> "<text>"` | stops the session of `#<n>` if it is alive and resumes it with the text |

## Coordinator loop

1. Session start: `.claude/hooks/supervisor-start.sh` hands the coordinator the digest; show the owner its "ждёт вас" and "PR на проверку" lines.
2. Start `/loop 10m bash .claude/skills/session-supervisor/scripts/supervisor.sh tick` without being asked — after `CronList` shows no such job, so a cleared or compacted session does not get a second one. On each tick tell the owner only what changed since the last one.
   The hook fires on `startup` only: a resumed session keeps its loop. A recurring job expires after 7 days; the next `claude` start brings it back.
3. The owner names work: file each issue with `start_issue_flow.sh --no-checkout`, then `launch` it. Work that needs the full stand (AGENTS.md, # Sessions and Worktrees) is not launched — it runs in the main checkout.
4. The owner answers a session (`#12: A`): `answer 12 "A"`.
5. The owner names a pull request to merge: `answer <n> "merge"` — the session merges and detaches its worktree; the next `tick` deletes it. A general "ok" names no pull request and merges nothing.

A session asks by ending its turn with `needs input:` — that is the "ждёт вас" line. It never waits inside a turn.

A `claude rm` refusal is printed verbatim in the tick output. Pass it to the owner; `--discard-unpushed` and `--force-remove-worktree` are the owner's call.
