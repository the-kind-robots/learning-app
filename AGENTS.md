# AGENTS

- When reporting verification, say clearly what was actually proven and what was not.
- For project structure, start at [readme.md](readme.md). It links to focused README files.

# Delivery

- Any task likely to end in a committed edit runs the full flow: invoke the `repo-task-delivery` skill and let it drive. It says when a narrower skill is enough.
- The flow: issue on the board -> branch from that issue -> implement -> verify -> PR -> merge -> Done.
- Issues come from the workflow, never by hand:
  `bash .claude/skills/gh-project-workflow/scripts/start_issue_flow.sh --title "..." --priority High --status "In progress" --base master --no-checkout`
  It creates or reuses the issue, puts it on the org project `Learning app` (`the-kind-robots`, number 11), sets Status and Priority, and links a branch without checking it out. `.claude/hooks/delivery-guard.sh` refuses a raw `gh issue create` and a PR from a branch with no issue number.
- Priority is **Urgent/High/Medium/Low** — judge it, never leave it empty. It is the organization's native issue field, not a board column: the script sets it, and reading it back needs the `ProjectV2ItemIssueFieldValue` fragment, or it looks unset when it is not.
- The board is the owner's only window into the work. An issue that is not on it does not exist.

Do not start the flow when the user asks a question, wants only reading or measuring, is continuing an issue that is already active, or says to skip GitHub/OpenSpec. Everything else goes through it — bug, refactor, docs, skills, config. "It is small" is not an exception.

Whether the work also needs an OpenSpec change: `repo-task-delivery`, step 3. Process rules — how the repository is worked — live in this file.

If the user asks for a blocked command outright, prefix it with `DELIVERY_GUARD=off`. That turns the refusal into a normal approval prompt, so the owner still confirms. Never reach for it on your own judgement.

A refusal — from a hook or from workspace isolation — stops the work and goes to the owner. Report what you were doing, what was refused, and the refusal verbatim. Do not look for a way around it: no commit assembled in a side worktree and pushed to the branch ref, no rewording a command past a static check.

**Behaviour is normative in exactly two files**: the change's delta spec and the synced main spec. The proposal, the design notes, the tasks, the ADR and the documentation link to it — they never restate it.

**Settle a decision before writing it down.** A decision about what a person does — which gesture is common, whether a control is a target or a label, what someone reads — cannot be settled by the codebase. Name those decisions before the first artifact. Take the measurement where one exists; otherwise ask the owner in one line with two named options. Until it is answered it is a line in the issue, not a spec.

**Design a screen before building it.** Does this work build a new screen, or change how an existing one looks or is laid out — a theme, a redesign, a new layout? Then whoever writes the first artifact (spec, design notes or code) invokes the `frontend-design:frontend-design` skill first. A point fix to a layout bug — an overflow, a misaligned control — does not.

**Verify the symptom where it appeared.** A fix proven on a fixture is not proven on the stand it was for. When the report came from a phone, the tunnel or the live nginx, the verification goes there before the work is called done.

# Sessions and Worktrees

- One issue, one background session. The interactive session files the issue (`start_issue_flow.sh --no-checkout`), launches `claude --bg -n "#N" "<task>"`, and watches sessions with `claude agents`.
- The background session calls `EnterWorktree`, then `git switch <issue branch>`, and does the work itself: edit, verify, PR. The harness refuses its writes to the main checkout.
- The interactive session works in the main checkout: reading, triage, measuring and full-stand work. Its edits pass through the owner's permission prompts.
- Full-stand work is a dependency test, not a topic match. Name the shared thing the work needs — a live CouchDB, nginx routing, a migration over real data — and it runs in the main checkout. Cannot name one? It runs in a background session's worktree.
- A worktree isolates files, not the runtime: CouchDB, nginx and the dev ports stay shared. It still proves browser behaviour on fixture data — it serves on `localhost`, a secure context, so OPFS, Web Locks, service workers and workers run there.
- Branches come from `gh issue develop` (the start script) only. Never `git checkout -b` or `git switch -c`; a permission rule asks on both.
- Run git commands plain and single: no pipes, `;` or `&&`.
- Worktrees are the harness's: it removes them with their sessions. A merge deletes the branch.
- Subagents work inside the caller's worktree: `ops` (Haiku; issue/board scripts, commit of staged work, push, draft PR), `log-reader` (Haiku; digest of logs, CI and test output), `Explore`.

# Issues

- The repository is public. Never state production facts in issues, PRs, commits or specs — what its passwords are not, what is currently broken there. Run such checks, report the results privately, and keep the tracker wording neutral ("a packaged deployment cannot override the dev credentials").
- Issues form a shallow DAG. Dependency is the native blocked-by relation: `gh issue edit <child> --add-blocked-by <blocker>`. A `Needs: #N` body line may mirror it for grep. No cycles.
- An issue exists only for work a pull request can close, a bug, or a decision worth recording. Findings, measurements and design notes are comments on the existing issue — never a new issue.
- Before filing, search open issues for an existing home; one issue may host several PRs.
- If a chain goes deeper than Needs -> Needs, that is planning disguised as tracking — merge the nodes.

# Commits

PRs merge by squash, so this governs the branch history the reviewer reads, not `master`.

- A branch carries as few commits as its changes need. Each is one logical, self-contained change: it builds and passes tests on its own, and its message says what changed.
- Unrelated changes never share a commit; one change is never split across several.
- No fixup, typo, WIP or "address review" commits survive to the merge. Fold them into the commit they fix: `git commit --amend`, or `git commit --fixup <sha>` then `GIT_SEQUENCE_EDITOR=: git rebase --autosquash master`.
- Review fixes are folded the same way, then pushed with `git push --force-with-lease` — only to the branch of your own issue, never `master`. The thread reply names the new hash.

# Browser / PWA Verification

- For browser-runtime behavior, use real browser proof, not only code inspection.
- Before Windows Chrome CDP, run `bash .claude/skills/learning-app-cdp/scripts/learning_app_cdp.sh doctor`.
- Chrome down (CDP endpoint silent, evals dying)? `learning_app_cdp.sh start-local`. Never launch chrome.exe by hand: the debug port needs the separate profile start-local provides.
- If Windows interop is broken (`cmd.exe` gives `exec format error` or `WSLInterop` is missing), ask for `wsl --shutdown`, then retry.
- For Service Worker offline reload checks, wait until `navigator.serviceWorker.controller` is true before switching offline.

# Project Skills

- Project skills live in `.claude/skills/`; invoke their scripts by that path.

# Token hygiene

- `git diff --stat` before a full diff; then diff only the files you need. History: `git log --oneline -20`.
- Hand subagents file paths, not pasted contents (e.g. "review `openspec/changes/<x>/VERIFY.md`").
- Run `openspec validate` once, right before archive — not after every edit.
- Browser suite while iterating: `npx playwright test <spec> --max-failures=1`. The local reporter is `line`.

# Tests

- The Playwright e2e suite is the behaviour spec. `npm run test:catalog` regenerates `test/BEHAVIOR.md`, its table of contents. New behaviour first appears as a line there — a test title — before code.
- Titles in Russian: `<actor> <action> → <result>`. Bodies use `test.step('Дано …')`, `test.step('Когда …')`, `test.step('Тогда …')`.
- Assert on what the user sees: roles, labels, text, visibility. Helpers are actions only — no assertions inside helpers.
- Unit tests only for pure domain algorithms: retention/scheduling, DB migrations, reconciliation/sync, parsers. `deftest` names are behaviour phrases; assert values with `=`; collapse near-duplicate cases with `are`.
- No tests for agent tooling (hooks, skills, build stamps, dev instrumentation, test guards).
