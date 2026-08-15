# AGENTS

- Assume caveman is already active by default in this repo.
- Do not wait for reminders to answer in caveman style.
- Be brief, direct, and technically precise.
- When reporting verification, say clearly what was actually proven and what was not.

- For project structure, start at [readme.md](readme.md). It links to focused README files.

# Delivery

- Any task likely to end in a committed edit runs the full flow. Invoke the `repo-task-delivery` skill and let it drive — do not assemble the steps by hand.
- The flow: issue on the board -> branch from that issue -> OpenSpec change -> implement -> verify -> archive -> PR -> merge -> Done -> cleanup.
- `repo-task-delivery` is the entrypoint. It delegates GitHub to `gh-project-workflow` and specs to the OpenSpec skills. Do not start at a sub-skill; they assume the flow is already underway.
- Issues come from the workflow, never by hand:
  `bash .skills/gh-project-workflow/scripts/start_issue_flow.sh --title "..." --priority Major --status "In progress" --base master`
  One call creates the issue, reuses an existing item of the same title, puts it on project `Learning app` (2), sets Status and Priority, and runs `gh issue develop --checkout`.
- A raw `gh issue create` makes an issue that is on no board, with no Status and no Priority — work nobody can see. #288, #289, #290 and #292 were made that way and had to be added by hand afterwards. A `PreToolUse` hook now refuses the raw form and names the script instead.
- The board is the owner's only window into the work. An issue that is not on it does not exist.

Do not start the flow when the user asks a question, wants only reading or measuring, is continuing an issue that is already active, or says to skip GitHub/OpenSpec. Everything else goes through it — bug, refactor, docs, skills, config. "It is small" is not an exception; it is the usual excuse, and it is what produced four issues nobody could see.

If the user asks for a blocked command outright, prefix it with `DELIVERY_GUARD=off`. That turns the refusal into a normal approval prompt, so the owner still confirms. Never reach for it on your own judgement.

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
  `gh api graphql -f query='mutation($i: ID!, $b: ID!) { addBlockedBy(input: {issueId: $i, blockingIssueId: $b}) { issue { number } } }' -f i=$(gh api repos/u473t8/learning-app/issues/<child> --jq .node_id) -f b=$(gh api repos/u473t8/learning-app/issues/<blocker> --jq .node_id)`
  A `Needs: #N` body line may mirror it for grep. No cycles.
- An issue exists only for work a pull request can close, a bug, or a decision worth recording. Findings, measurements and design notes are comments on the existing issue — never a new issue.
- Before filing, search open issues for an existing home; one issue may host several PRs.
- If a chain goes deeper than Needs -> Needs, that is planning disguised as tracking — merge the nodes.

# Branches and Worktrees

- Create branches only with `gh issue develop <number> --checkout`, so the branch is linked to its issue on GitHub. Never `git checkout -b` for tracked work.
- The built-in worktree mechanism branches from `master` on its own, which would leave the issue with no development link — that is how #289 ended up on a branch GitHub does not know about. Order that satisfies both rules: `gh issue develop <number>` first, then `git worktree add .claude/worktrees/<slug> <branch>`, then enter that path with `EnterWorktree` (it takes an existing worktree path, not only a new name). Working in the main checkout, plain `gh issue develop <number> --checkout` is enough.
- A worktree isolates files, not the runtime: CouchDB, nginx and the dev ports stay shared, and a fresh checkout costs about a gigabyte here plus a cold shadow-cljs cache.
  - **Main worktree** — anything needing the full stand: sync, dictionary, migrations, schema, anything talking to CouchDB or nginx.
  - **Worktree** — work needing only the compiler and tests: refactors, style, docs, skills, tests, small UI fixes.
- Worktrees come from the built-in mechanism (`EnterWorktree` / `ExitWorktree`), which keeps them in `.claude/worktrees/`. Do not create them by hand and do not invent other locations.
- On merge, delete the branch and remove the worktree. Both accumulate silently otherwise.

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
