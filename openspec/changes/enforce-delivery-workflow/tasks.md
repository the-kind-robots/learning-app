## 1. Paths that resolve everywhere

- [x] 1.1 `.skills/gh-project-workflow/SKILL.md`: repo-relative `.codex/skills/...` -> `.skills/...` (first — the deny messages quote these paths)
- [x] 1.2 `.skills/learning-app-cdp/SKILL.md`: same, leaving every `${HOME}/.codex/...` user-global path untouched
- [x] 1.3 `AGENTS.md` CDP invocation and a note under `# Project Skills` that `.codex/` is absent in worktrees
- [x] 1.4 `.claude/settings.json`: allow the `.skills/...` and `.claude/skills/...` script paths

## 2. The rules, where a session can see them

- [x] 2.1 `AGENTS.md`: a `# Delivery` section — sequence, entrypoint skill, exceptions — placed before the sections that presuppose it
- [x] 2.2 `AGENTS.md`: resolve the branch-rule vs `EnterWorktree` contradiction with the order that satisfies both
- [x] 2.3 `CLAUDE.md`: `@AGENTS.md` import instead of the markdown link
- [x] 2.4 `.claude/rules/repo-delivery.md`: no frontmatter at all, so the harness loads it at session start

## 3. The block

- [x] 3.1 `.claude/hooks/delivery-guard.sh`: deny raw issue creation and PRs from branches with no issue number; ask on `git checkout -b`; pass everything else
- [x] 3.2 Each refusal names the replacement command and the `DELIVERY_GUARD=off` escape
- [x] 3.3 Wire it as `PreToolUse` on `Bash` in `.claude/settings.json`, resolving the script through `CLAUDE_PROJECT_DIR` with a fallback to the session's own checkout

## 4. Verify

- [x] 4.1 Matcher checks, 21/21: raw create denied; `issue view/list/develop/comment`, `pr list/view`, `project item-add/item-list`, `gh api`, the wrapper script, and `echo`/`git commit`/`grep` mentioning the command all pass; heredoc bodies pass; `git checkout -b` and `git switch -c` ask; `DELIVERY_GUARD=off` asks; env prefixes and `&&` chains still deny
- [x] 4.2 `gh pr create` denied on `master`, allowed on `293-...` — checked from both checkouts
- [x] 4.3 The settings.json command string resolves the script with `CLAUDE_PROJECT_DIR` pointing at the worktree, at the main checkout, and unset — deny in all three
- [x] 4.4 The wrapper script runs unblocked end to end — it created #293 with Status and Priority set
- [x] 4.5 A live session refuses `gh issue create` — and does so despite `Bash(gh *)` sitting in the allowlist, which was the one assumption that could have voided the design. The refusal text reaches the model as the tool result, so it can act on it. `gh issue view`, `gh pr create` from the issue branch, and the wrapper script all pass in the same session.
- [x] 4.6 A fresh headless session on this branch, with every file tool disallowed, quotes the `# Delivery` section from `AGENTS.md` (so the `@` import loads) and lists `.claude/rules/repo-delivery.md` as the one rules file it can see — the two `paths:`-scoped rules stay out until a Clojure file is touched, which is the intended split. The same probe run in the main checkout answers `NOT IN CONTEXT`, so the rule is visible because of these files and nothing else
- [x] 4.7 New files are tracked and reach a worktree through git
- [x] 4.8 CI green on the pull request — the test jobs skip, since the change touches no application code, and a skipped job satisfies branch protection by design (#204)
