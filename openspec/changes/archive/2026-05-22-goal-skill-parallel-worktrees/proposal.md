## Why

Running multiple AI-driven tasks simultaneously requires each session to have its own isolated git worktree with dedicated server ports so processes do not conflict. Without a structured skill, port collisions and manual setup make parallel development impractical.

## What Changes

- Add `/goal` skill (`goal-skill-parallel-worktrees`) to `.skills/goal/` — autonomous end-to-end task delivery in a worktree, stopping only for final PR review.
- Add slot management scripts: `claim-slot.sh`, `release-slot.sh`, `gen-nginx-slot.sh` — atomic port allocation via `flock`, nginx vhost generation per slot.
- Add `LEARNING_APP_PORT` env var support to `src/backend/core.clj` — backend port is now overridable so parallel worktrees can run on distinct ports.
- Add `.worktree-backend.log` / `.worktree-backend.pid` to `.gitignore` — per-worktree server artifacts stay out of git.

## Capabilities

### New Capabilities

- `goal-skill`: The `/goal <task>` slash command. Orchestrates worktree creation, port slot claiming, server startup, autonomous `repo-task-delivery` execution, and a final review gate.

### Modified Capabilities

- `repo-agent-infrastructure`: Backend now reads `LEARNING_APP_PORT` env var (falls back to 8083). Slot registry added at `.git/worktree-slots/` (outside tracked tree). New gitignore entries for worktree server artifacts.

## Impact

- `src/backend/core.clj` — one-line change to `(def port ...)`.
- `.skills/goal/` — new skill directory (tracked via `.skills/`, symlinked into `.claude/skills/` and `.codex/skills/`).
- `.gitignore` — two new entries.
- No user-facing behavior change; only dev-environment tooling.
