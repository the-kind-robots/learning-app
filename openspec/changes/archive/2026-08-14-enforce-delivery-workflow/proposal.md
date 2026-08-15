## Why

`repo-delivery-workflow` already requires that likely repository changes default to
tracked delivery, and `repo-task-delivery` spells the flow out in detail. Neither reaches
the session. #289 was implemented, verified and shipped without the flow — issue and PR
came from raw `gh` commands, so nothing appeared on the board; #288, #290 and #292 turned
out to be off it as well. GitHub issue: #293.

The rule was not ignored. It was never in context: the flow is written only inside a skill
body, `CLAUDE.md` links `AGENTS.md` instead of importing it, and no hook says anything at
session start. A requirement nothing delivers is a requirement nothing enforces.

## What Changes

- A `# Delivery` section in `AGENTS.md`: the sequence, `repo-task-delivery` as the
  entrypoint, and the cases where the flow does not apply.
- `CLAUDE.md` imports `AGENTS.md` with `@AGENTS.md` instead of linking it, so repo rules
  load at session start.
- `.claude/rules/repo-delivery.md` — a rules file with no `paths:` key, which the harness
  loads unconditionally at session start. Second, independent channel.
- A `PreToolUse` hook in the repo's `.claude/settings.json` (git-tracked, so it reaches
  every worktree) refusing raw `gh issue create`, and `gh pr create` from a branch with no
  issue number. The workflow scripts are unaffected by construction: the hook sees only
  the agent's own command string, never the `gh` calls a script makes in a subprocess.
- `AGENTS.md` resolves the contradiction between "branches only from `gh issue develop`"
  and "worktrees only from `EnterWorktree`" — the built-in mechanism branches from master,
  so worktree work breaks the branch rule by default and leaves the issue with no
  development link.
- Repo-relative `.codex/skills/...` paths become `.skills/...`. The `.codex` symlink is
  gitignored and absent in worktrees, so those invocations — including the ones for the
  scripts this change depends on — cannot resolve there. `${HOME}/.codex/skills/...` is a
  user-global path and stays.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `repo-delivery-workflow`: tracked delivery stops being advisory — the flow's rules SHALL
  be present in every session, and the commands that bypass it SHALL be refused.
- `repo-agent-infrastructure`: skill invocations SHALL use a path that resolves in a
  worktree.

## Impact

- `AGENTS.md`, `CLAUDE.md` — the normative text and its delivery into context.
- `.claude/rules/repo-delivery.md` (new), `.claude/hooks/delivery-guard.sh` (new),
  `.claude/settings.json` — the always-on rule and the block.
- `.skills/gh-project-workflow/SKILL.md`, `.skills/learning-app-cdp/SKILL.md` — path fix.
- No application code.
