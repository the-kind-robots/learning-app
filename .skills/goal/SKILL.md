---
name: goal
description: "Autonomous task execution in an isolated git worktree. Sets up the worktree, starts servers, runs the full repo-task-delivery workflow, and stops only for final PR review. Use when the user passes a task description and wants it delivered without interruption."
---

# /goal — Autonomous Worktree Task Delivery

Invoke as `/goal <task description>`.

This skill runs the full delivery workflow for one task, isolated in its own git worktree.

## Phase 0 — One clarifying question

Ask a single focused question only if the task description is genuinely ambiguous about scope or target area. If the description is clear enough to start, skip directly to Phase 1.

Do not ask about process, tooling, or preferences — those are fixed by this skill.

## Phase 1 — Worktree setup

**If already running inside a worktree** (i.e. `git rev-parse --show-toplevel` returns a path different from the main repo root — check via `git rev-parse --git-common-dir` returning `<root>/.git` vs just `.git`): skip creation, use current worktree.

**Otherwise, create the worktree:**

1. Derive a slug from the task (e.g. `feature/add-theme-tags`).
2. Create the worktree at `.worktrees/<slug>/` inside the main repo:
   ```bash
   mkdir -p .worktrees
   git worktree add .worktrees/<slug> -b <branch-name>
   cd .worktrees/<slug>
   ```
3. Verify `.worktrees/` is gitignored in the main repo (it should be — if not, add it).

`.claude/settings.json` and `.claude/rules/` are committed to git, so the worktree inherits them automatically — no manual copy needed.

Announce: "Worktree ready: `.worktrees/<slug>` on branch `<branch>`"

## Phase 2 — Server startup

Start the Clojure backend in the background on the default port:

```bash
clj -M:dev -m core &> .worktree-backend.log &
echo $! > .worktree-backend.pid
```

Wait for the backend to become responsive (poll `curl -s http://localhost:8083/health` up to 30 s).

Do NOT start `shadow-cljs watch` — use `npx shadow-cljs compile app` for CLJS verification steps.

## Phase 3 — Autonomous implementation

Run the full `repo-task-delivery` workflow:

1. GitHub issue + project item (via `gh-project-workflow`)
2. OpenSpec change (via `openspec-propose-change`)
3. Implementation (via `openspec-apply-change`)
4. Verification:
   - Compile CLJS: `npx shadow-cljs compile app`
   - Run Clojure tests if any: `clj -M:test`
   - Smoke-test the backend: curl key endpoints on `http://localhost:8083`
5. OpenSpec sync/archive (via `openspec-archive-change`)

Do NOT pause for questions during this phase. On ambiguity, make a reasonable decision, note it in the commit message or PR description, and continue.

On hard blockers (build fails, dependency missing, test cannot pass): stop, describe the blocker clearly, and wait for user input.

## Phase 4 — Review gate

Stop and announce:

```
═══════════════════════════════════════════════════════
READY FOR REVIEW
  Branch:  <branch>
  PR:      <gh-pr-url>
  Backend: http://localhost:8083  (already running)
═══════════════════════════════════════════════════════
Waiting for your approval. Say "merge" to merge the PR,
or give feedback to continue.
```

## Phase 5 — Merge and cleanup

On user approval ("merge", "ok", "go", "lgtm", or equivalent):

1. Merge the PR (via `gh pr merge --squash` or as the repo uses)
2. Kill the background backend process: `kill $(cat .worktree-backend.pid) 2>/dev/null`
3. Optionally remove the worktree: `git worktree remove <path>` (ask if unsure whether the user wants it kept)

On feedback: implement the requested changes, re-verify, update the PR, and return to Phase 4.

## Error handling

- If backend fails to start within 30 s: print `.worktree-backend.log` tail and stop
- If CLJS compile fails: print the error, fix it before proceeding
- If tests fail: fix them before proceeding to Phase 4
