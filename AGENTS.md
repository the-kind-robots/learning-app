# AGENTS

- Assume caveman is already active by default in this repo.
- Do not wait for reminders to answer in caveman style.
- Be brief, direct, and technically precise.
- When reporting verification, say clearly what was actually proven and what was not.

- For project structure, start at [readme.md](readme.md). It links to focused README files.

# Delivery

- Any task likely to end in a committed edit runs the full flow. Invoke the `repo-task-delivery` skill and let it drive — do not assemble the steps by hand.
- The flow: issue on the board -> branch from that issue -> implement -> verify -> PR -> merge -> Done -> cleanup. Work that changes product behaviour adds an OpenSpec change before implementing and archives it on the branch before the PR; the test for that is below.
- `repo-task-delivery` is the entrypoint. It delegates GitHub to `gh-project-workflow` and specs to the OpenSpec skills. Do not start at a sub-skill; they assume the flow is already underway.
- Issues come from the workflow, never by hand:
  `bash .skills/gh-project-workflow/scripts/start_issue_flow.sh --title "..." --priority High --status "In progress" --base master`
  One call creates the issue, reuses an existing item of the same title, puts it on the org project `Learning app` (`the-kind-robots`, number 11), sets Status and Priority, and runs `gh issue develop --checkout`.
- Priority is **Urgent/High/Medium/Low** and lives on the organization's native issue field, not on the board. The project refuses to write a column backed by an issue field, so the script sets it with `setIssueFieldValue`; reading it back needs the `ProjectV2ItemIssueFieldValue` fragment, or the value looks unset when it is not. The retired `Blocker/Critical/Major/Minor/Trivial` are rejected by name.
- The same call renames the running Claude Code session to `<number> <issue title>`, so the session list says which issue each session is on. Skipped silently outside Claude Code; a failed rename never fails the flow.
- A raw `gh issue create` makes an issue that is on no board, with no Status and no Priority — work nobody can see. #288, #289, #290 and #292 were made that way and had to be added by hand afterwards. A `PreToolUse` hook now refuses the raw form and names the script instead.
- The board is the owner's only window into the work. An issue that is not on it does not exist.
- The delegation rule under **Branches and Worktrees** is enforced, not left to memory. A second `PreToolUse` hook, `.claude/hooks/coordinator-guard.sh`, refuses `Edit`/`Write`/`NotebookEdit` from a session with no subagent id when the target is inside the repository, and asks instead of refusing when the target's worktree is on an issue branch, since that is also what legitimate full-stand work looks like. Subagents pass untouched. The same hook also watches `Bash`, where it only ever asks: an editor target is given in the payload, a shell target is guessed from a string, and its list of shell write shapes is short and knowingly incomplete. Its header comment records what it cannot tell apart — read it before trusting it, and do not read it as a lock. Since 2.1.143 `worktree.bgIsolation` refuses a background coordinator's editor writes natively; the hook's own coverage is the interactive session and shell writes.

Do not start the flow when the user asks a question, wants only reading or measuring, is continuing an issue that is already active, or says to skip GitHub/OpenSpec. Everything else goes through it — bug, refactor, docs, skills, config. "It" is the issue, the branch and the pull request; whether an OpenSpec change joins them is the test below. "It is small" is not an exception; it is the usual excuse, and it is what produced four issues nobody could see.

Whether the work also needs an OpenSpec change is a verifiability test, not a topic match. Name the behaviour this change alters that someone could check afterwards without reading the diff — a word that now syncs, a document shape the migration must produce, a screen that now answers differently. Can you name one? Write the OpenSpec change, and archive it on the branch before the PR. Cannot name one? There is no requirement to state, so there is no OpenSpec change: the work alters how the repository is worked, not what the product does. Subsystems are examples, never the test.

Repository process fails that test by construction — agent rules, hooks, skills, scripts, CI configuration, documentation about how work is delivered. Until #400 every tracked edit needed a change anyway, and since `openspec validate` refuses a change with no delta, process work had to invent a normative requirement about itself to get delivered. It no longer does. Process rules are written down here instead: this file is loaded every session, and the one other copy was deleted for saying the same things twice (#388).

Nothing else moves. The issue on the board, the branch from that issue, the guards, the pull request: unchanged, whichever side of the test the work falls on. Those are what make work visible — #288, #289, #290 and #292 were lost because they were never issues, not because they had no spec.

If the user asks for a blocked command outright, prefix it with `DELIVERY_GUARD=off`. That turns the refusal into a normal approval prompt, so the owner still confirms. Never reach for it on your own judgement.

A refusal — from one of these hooks or from workspace isolation — stops the work and goes to the owner. Report three things: what you were doing, what was refused, and the refusal verbatim. The owner decides what happens next. Do not look for a way around it. Named, because each has happened:

- Do not assemble a commit in a side or detached worktree and push straight to the branch ref because ordinary writes were blocked. That was PR #347; the harness flagged it afterwards rather than preventing it, and absence of prevention is not permission.
- Do not reword a command so it slips past a static check. Run it the permitted way or stop.
- Do not set `DELIVERY_GUARD=off` on your own judgement. Owner's request only, as above.

The guards read command text and the working directory, so they stop accidents, not intent. Going around one does not break the protection — it breaks the assurance that delivered work went down a verified path.

# Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

**Discover nREPL servers:**

`clj-nrepl-eval --discover-ports`

**Evaluate code:**

`clj-nrepl-eval -p <port> "<clojure-code>"`

With timeout (milliseconds)

`clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"`

The REPL session persists between evaluations - namespaces and state are maintained.
Always use `:reload` when requiring namespaces to pick up changes.

# Issues

- The repository is public. Never state production facts in issues, PRs, commits or specs — what its passwords are not, what is currently broken there. Run such checks, report the results privately, and keep the tracker wording neutral ("a packaged deployment cannot override the dev credentials").
- Issues form a shallow DAG. Dependency is the native blocked-by relation, visible on the issue and the board:
  `gh api graphql -f query='mutation($i: ID!, $b: ID!) { addBlockedBy(input: {issueId: $i, blockingIssueId: $b}) { issue { number } } }' -f i=$(gh api repos/the-kind-robots/learning-app/issues/<child> --jq .node_id) -f b=$(gh api repos/the-kind-robots/learning-app/issues/<blocker> --jq .node_id)`
  A `Needs: #N` body line may mirror it for grep. No cycles.
- An issue exists only for work a pull request can close, a bug, or a decision worth recording. Findings, measurements and design notes are comments on the existing issue — never a new issue.
- Before filing, search open issues for an existing home; one issue may host several PRs.
- If a chain goes deeper than Needs -> Needs, that is planning disguised as tracking — merge the nodes.

# Branches and Worktrees

**One rule: everything under your own worktree root is yours, nothing outside it is.** A worktree nested under your root is writable, editor tools included. A sibling is not: `EnterWorktree` onto one claims success and moves nothing — every later Bash call is refused, `pwd` included, until you re-enter your own path. `git -C <path outside your worktree>` is refused on the string; drop the redirect and the same command runs.

- Branches come from `gh issue develop <number>` only, so the branch is linked to its issue. Never `git checkout -b` or `git switch -c` — a permission rule in `.claude/settings.json` asks on both — and never let the worktree mechanism make the branch: it branches from `master` and leaves the issue with no development link (#289).
- The coordinating session stays in the main checkout and delegates every repository edit, including one it would rather make itself.
- Hand work to the `executor` agent (`.claude/agents/executor.md`). Its definition carries the worktree isolation and the branch recipe.
- In the main checkout, `gh issue develop <number> --checkout` is enough.
- A worktree isolates files, not the runtime: CouchDB, nginx and the dev ports stay shared.
- Where to work is a dependency test, not a topic match. Name the shared thing the work needs — a live CouchDB, nginx routing, a migration over real data — and it stays in the main checkout. Cannot name one? It goes to the executor. Subsystems are examples, never the test: #351 was sent to the main checkout for touching the dictionary and needed nothing shared.
- A worktree still proves browser behaviour on fixture data. It serves on `localhost`, a secure context, so OPFS, Web Locks, service workers and workers all run there; the browser tests' fixture dictionary is enough for anything that needs only one origin. Only the real data and the shared services stay behind — cold-start timing on the full dictionary, replication against a live CouchDB, nginx routing.
- On merge, delete the branch and remove the worktree.

Measured 2026-09-10 on Claude Code 2.1.267; the boundary is a path-prefix check on your pinned root and belongs to the harness. `worktree.bgIsolation` in `.claude/settings.json` is the owner's lever over it, not yours. Re-measure after a CLI update.

# Browser / PWA Verification

- For browser-runtime behavior, use real browser proof, not only code inspection.
- Before Windows Chrome CDP, run `bash .skills/learning-app-cdp/scripts/learning_app_cdp.sh doctor`.
- Chrome down (CDP endpoint silent, evals dying)? `learning_app_cdp.sh start-local`. Never launch chrome.exe by hand: the debug port needs the separate profile start-local provides.
- If Windows interop is broken (`cmd.exe` gives `exec format error` or `WSLInterop` is missing), ask for `wsl --shutdown`, then retry.
- For Service Worker offline reload checks, wait until `navigator.serviceWorker.controller` is true before switching offline.

# Project Skills

- Shared project skills source lives in `.skills/`.
- Install/relink tool locations with `.skills/install.sh --force`.
- Expected links: `.codex/skills -> ../.skills` and `.claude/skills -> ../.skills`.
- Invoke skill scripts by their real path, `.skills/...`. `.codex/` is gitignored and absent in worktrees, so a `.codex/skills/...` command works in the main checkout and fails everywhere else; `.claude/skills/...` is a tracked symlink to the same place.
