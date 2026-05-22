---
name: goal
description: "Autonomous task execution in an isolated git worktree. Sets up the worktree, allocates ports, starts servers, runs the full repo-task-delivery workflow, and stops only for final PR review. Use when the user passes a task description and wants it delivered without interruption."
---

# /goal — Autonomous Worktree Task Delivery

Invoke as `/goal <task description>`.

This skill runs the full delivery workflow for one task, isolated in its own git worktree, using dedicated ports to coexist with other parallel `/goal` sessions.

## Phase 0 — One clarifying question

Ask a single focused question only if the task description is genuinely ambiguous about scope or target area. If the description is clear enough to start, skip directly to Phase 1.

Do not ask about process, tooling, or preferences — those are fixed by this skill.

## Phase 1 — Worktree setup

Use `using-git-worktrees` to create (or detect) an isolated worktree for this task.

- Branch name: derive from the task description (e.g. `feature/add-theme-tags`)
- If already running inside a worktree (current dir ≠ main repo root), skip creation and use current worktree
- Announce: "Worktree ready: `<path>` on branch `<branch>`"

## Phase 2 — Port slot allocation

Run the claim script from the worktree root:

```bash
bash .skills/goal/scripts/claim-slot.sh "$(pwd)"
```

This outputs a slot number N ≥ 1. Derive ports:

| Resource       | Port formula       | Example (slot 1) |
|----------------|--------------------|------------------|
| Backend        | 8083 + N×100       | 8183             |
| shadow-cljs    | 9630 + N×100       | 9730             |
| nREPL          | 4444 + N×100       | 4544             |
| HTTPS preview  | 4430 + N           | 4431             |

Announce: "Slot N — backend :PORT_B, shadow-cljs :PORT_S, preview: https://sprecha.local:PORT_H"

## Phase 3 — Server startup

Start the Clojure backend in the background on the allocated port:

```bash
LEARNING_APP_PORT=<backend-port> clj -M:dev -m core &> .worktree-backend.log &
echo $! > .worktree-backend.pid
```

Wait for the backend to become responsive (poll `curl -s http://localhost:<backend-port>/health` or any endpoint, up to 30 s).

Do NOT start `shadow-cljs watch` — use `npx shadow-cljs compile app` for CLJS verification steps. This avoids dev-server port conflicts while agents run in parallel.

## Phase 4 — Autonomous implementation

Run the full `repo-task-delivery` workflow:

1. GitHub issue + project item (via `gh-project-workflow`)
2. OpenSpec change (via `openspec-propose-change`)
3. Implementation (via `openspec-apply-change`)
4. Verification:
   - Compile CLJS: `npx shadow-cljs compile app`
   - Run Clojure tests if any: `clj -M:test`
   - Smoke-test the backend: curl key endpoints on `http://localhost:<backend-port>`
5. OpenSpec sync/archive (via `openspec-archive-change`)

Do NOT pause for questions during this phase. On ambiguity, make a reasonable decision, note it in the commit message or PR description, and continue.

On hard blockers (build fails, dependency missing, test cannot pass): stop, describe the blocker clearly, and wait for user input.

## Phase 5 — Nginx config (for browser review)

Generate the nginx vhost config for this slot:

```bash
bash .skills/goal/scripts/gen-nginx-slot.sh <slot> <certs-dir>
```

Where `<certs-dir>` is the directory used in the existing `sprecha.local` nginx config (typically the value of `$NGINX_CERTS_DIR` set during initial setup — check `infra/development/etc/nginx/sites-available/sprecha.local.template` or the installed nginx config).

Print the generated config and the activation commands so the user can copy-paste them when they want to preview.

## Phase 6 — Review gate

Stop and announce:

```
═══════════════════════════════════════════════════════
READY FOR REVIEW
  Branch:   <branch>
  PR:       <gh-pr-url>
  Preview:  https://sprecha.local:<https-port>/
            (run the nginx commands above, then sudo nginx -s reload)
  Backend:  http://localhost:<backend-port>  (already running)
═══════════════════════════════════════════════════════
Waiting for your approval. Say "merge" to merge the PR,
or give feedback to continue.
```

## Phase 7 — Merge and cleanup

On user approval ("merge", "ok", "go", "lgtm", or equivalent):

1. Merge the PR (via `gh pr merge --squash` or as the repo uses)
2. Kill the background backend process: `kill $(cat .worktree-backend.pid) 2>/dev/null`
3. Release the port slot: `bash .skills/goal/scripts/release-slot.sh <slot>`
4. Optionally remove the worktree: `git worktree remove <path>` (ask if unsure whether the user wants it kept)

On feedback: implement the requested changes, re-verify, update the PR, and return to Phase 6.

## Error handling

- If `claim-slot.sh` fails: print the error and ask the user to check `.git/worktree-slots/`
- If backend fails to start within 30 s: print `.worktree-backend.log` tail and stop
- If CLJS compile fails: print the error, fix it before proceeding
- If tests fail: fix them before proceeding to Phase 5

## Port reference quick table

| Slot | Backend | shadow-cljs | nREPL | HTTPS preview |
|------|---------|-------------|-------|---------------|
| 0    | 8083    | 9630        | 4444  | 443 (main)   |
| 1    | 8183    | 9730        | 4544  | 4431          |
| 2    | 8283    | 9830        | 4644  | 4432          |
| 3    | 8383    | 9930        | 4744  | 4433          |
