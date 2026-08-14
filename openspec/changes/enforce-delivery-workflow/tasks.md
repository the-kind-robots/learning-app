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
- [ ] 4.5 A live session refuses `gh issue create`: hooks load at session start, so this needs a session started after this branch is checked out. Owner to confirm.
- [ ] 4.6 The rule text is in a fresh session's context, in the main checkout and in a worktree
- [x] 4.7 New files are tracked and reach a worktree through git
- [ ] 4.8 CI green on the pull request
