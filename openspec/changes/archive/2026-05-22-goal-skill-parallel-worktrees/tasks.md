## 1. Backend port override

- [x] 1.1 Add `LEARNING_APP_PORT` env var to `src/backend/core.clj` (fallback 8083)

## 2. Slot management scripts

- [x] 2.1 Write `claim-slot.sh` — flock-based atomic slot claim with stale detection via `git worktree list`
- [x] 2.2 Write `release-slot.sh` — remove slot directory from registry
- [x] 2.3 Write `gen-nginx-slot.sh` — emit nginx vhost config for slot N (port 4430+N, backend 8083+N×100)

## 3. /goal skill

- [x] 3.1 Write `.skills/goal/SKILL.md` with full autonomous workflow: one question → worktree → slot → servers → repo-task-delivery → review gate → cleanup

## 4. Gitignore

- [x] 4.1 Add `.worktree-backend.log` and `.worktree-backend.pid` to `.gitignore`
