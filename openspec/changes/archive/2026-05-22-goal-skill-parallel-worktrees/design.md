## Context

The repo uses a `repo-task-delivery` skill that drives one AI agent through a full GitHub issue → OpenSpec → implement → verify → PR loop. Opening two Claude Code windows on the same worktree causes port collisions (backend 8083, shadow-cljs nREPL 4444, dev server 9630) and makes parallel AI sessions impractical.

The goal is a `/goal` slash command that sets up an isolated worktree with dedicated ports, runs `repo-task-delivery` autonomously, and pauses only for final review.

Existing skills: `using-git-worktrees`, `repo-task-delivery`, `gh-project-workflow`, `openspec-*`. These are reused as sub-steps.

## Goals / Non-Goals

**Goals:**
- Allow N concurrent `/goal` sessions on the same machine without port conflicts
- Fully autonomous: no mid-task interruptions except hard blockers
- One clarifying question before start, then quiet until "READY FOR REVIEW"
- Port slot registry is crash-safe: stale slots are detected via git worktree list

**Non-Goals:**
- CouchDB data isolation (parallel tasks are code-only changes; DB state is not mutated)
- Automated nginx reload (requires sudo; user reloads manually for browser review)
- shadow-cljs watch per worktree (agents use `compile` for verification, not hot-reload)

## Decisions

### D1: Slot-based port allocation via directory registry

Each worktree claims a numbered slot ≥ 1 in `.git/worktree-slots/<N>/worktree`. Port offsets are `base + N×100`:
- Backend: `8083 + N×100`
- shadow-cljs dev server: `9630 + N×100`
- nREPL: `4444 + N×100`
- HTTPS preview: `4430 + N`

**Why directory registry over JSON:** `mkdir` is atomic on POSIX filesystems; no need for jq. `flock` on a shared lock file serializes concurrent claims.

**Stale detection via git worktree list:** A slot is stale when its worktree path is no longer registered in `git worktree list`. This is reliable — removing a worktree is the canonical cleanup step.

**Alternative considered:** PID-based staleness check. Rejected: shell PIDs get recycled quickly; a new process could falsely appear alive.

### D2: `compile` for CLJS verification, not `watch`

Agents run `npx shadow-cljs compile app` for correctness checks instead of starting a watch process. This avoids needing to multiplex the shadow-cljs HTTP dev server (port 9630+) across worktrees.

**Why:** Parallel agents do not need hot-reload. They need compile-time correctness and a running backend for smoke tests. Starting a watch server per worktree would require patching `shadow-cljs.edn` in each worktree and is unnecessary complexity.

### D3: `LEARNING_APP_PORT` env var for backend

`(def port (or (some-> (System/getenv "LEARNING_APP_PORT") Integer/parseInt) 8083))`

Follows the existing `LEARNING_APP_*` naming convention. Default is unchanged.

### D4: nginx config generated but not auto-reloaded

`gen-nginx-slot.sh` emits a ready-to-paste nginx vhost that listens on `sprecha.local:<4430+N>`. User pastes it and runs `sudo nginx -s reload` manually when they want browser access during review. Automation would require `sudo` NOPASSWD for nginx, which is not set up.

## Risks / Trade-offs

- **Risk: Slot leak on hard session crash** → Mitigation: stale detection on next `claim-slot.sh` call cleans up orphaned slots automatically.
- **Risk: Port arithmetic collides with other local services** → Mitigation: 100-port offset gives wide separation; documented in skill's port table.
- **Risk: Skill diverges from repo workflow evolution** → Mitigation: `/goal` delegates to existing skills (`repo-task-delivery`, `using-git-worktrees`) so updates to those propagate automatically.

## Migration Plan

No migration needed. Existing single-session workflow is unchanged. The `LEARNING_APP_PORT` change is backward-compatible (default unchanged). Slot registry lives in `.git/` so it is never committed.

## Open Questions

None.
