# AGENTS

- Assume caveman is already active by default in this repo.
- Do not wait for reminders to answer in caveman style.
- Be brief, direct, and technically precise.
- When reporting verification, say clearly what was actually proven and what was not.

- For project structure, start at [readme.md](readme.md). It links to focused README files.

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
- A worktree isolates files, not the runtime: CouchDB, nginx and the dev ports stay shared, and a fresh checkout costs about a gigabyte here plus a cold shadow-cljs cache.
  - **Main worktree** — anything needing the full stand: sync, dictionary, migrations, schema, anything talking to CouchDB or nginx.
  - **Worktree** — work needing only the compiler and tests: refactors, style, docs, skills, tests, small UI fixes.
- Worktrees come from the built-in mechanism (`EnterWorktree` / `ExitWorktree`), which keeps them in `.claude/worktrees/`. Do not create them by hand and do not invent other locations.
- On merge, delete the branch and remove the worktree. Both accumulate silently otherwise.

# Browser / PWA Verification

- For browser-runtime behavior, use real browser proof, not only code inspection.
- Before Windows Chrome CDP, run `bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh doctor`.
- Chrome down (CDP endpoint silent, evals dying)? `learning_app_cdp.sh start-local`. Never launch chrome.exe by hand: the debug port needs the separate profile start-local provides.
- If Windows interop is broken (`cmd.exe` gives `exec format error` or `WSLInterop` is missing), ask for `wsl --shutdown`, then retry.
- For Service Worker offline reload checks, wait until `navigator.serviceWorker.controller` is true before switching offline.

# Project Skills

- Shared project skills source lives in `.skills/`.
- Install/relink tool locations with `.skills/install.sh --force`.
- Expected links: `.codex/skills -> ../.skills` and `.claude/skills -> ../.skills`.
