# AGENTS

- Assume caveman is already active by default in this repo.
- Do not wait for reminders to answer in caveman style.
- Be brief, direct, and technically precise.
- When reporting verification, say clearly what was actually proven and what was not.

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

# Browser / PWA Verification

- For browser-runtime behavior, use real browser proof, not only code inspection.
- Before Windows Chrome CDP, run `bash .codex/skills/learning-app-cdp/scripts/learning_app_cdp.sh doctor`.
- If Windows interop is broken (`cmd.exe` gives `exec format error` or `WSLInterop` is missing), ask for `wsl --shutdown`, then retry.
- For Service Worker offline reload checks, wait until `navigator.serviceWorker.controller` is true before switching offline.

# Project Skills

- Shared project skills source lives in `.skills/`.
- Install/relink tool locations with `.skills/install.sh --force`.
- Expected links: `.codex/skills -> ../.skills` and `.claude/skills -> ../.skills`.
