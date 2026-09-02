## 1. Move to native

- [x] 1.1 Add `.claude/agents/executor.md`: `name`, one-line `description`, `isolation: worktree`; body is the standing briefing (read `AGENTS.md`; plain `git checkout <branch>` in your own worktree, nested `wt-<n>` only when another worktree holds the branch, never `EnterWorktree`; never `git -C` outside your root; a refusal stops the work and is reported verbatim; stage explicitly; do not merge; report proved vs. trusted).
- [x] 1.2 `.claude/settings.json`: `permissions.ask` with `Bash(git checkout -b *)` and `Bash(git switch -c *)`. Remove the `git` arm from `.claude/hooks/delivery-guard.sh`; the `gh` arms stay as they are.
- [x] 1.3 `git rm .claude/rules/repo-delivery.md`; no live reference remains outside the OpenSpec archive.
- [x] 1.4 `.skills/repo-task-delivery/SKILL.md`: where-work-happens names the main checkout versus the `executor` agent; cleanup no longer says `ExitWorktree`. Sentence-level edits only.
- [x] 1.5 `AGENTS.md` **# Branches and Worktrees**: hand work to the `executor` agent; drop the by-hand `isolation: "worktree"` bullet and the pinned-agent recipe; keep the one rule, #289, the coordinator rule, the checkout-vs-executor split, the `/wt-*/` clause, cleanup on merge; re-date to 2026-09-02 / 2.1.258. Line 20: `worktree.bgIsolation` covers the background coordinator natively.
- [x] 1.6 `.claude/hooks/coordinator-guard.sh` header: the same `bgIsolation` note. Comment lines only.

## 2. Verify

- [x] 2.1 `bash -n` on both hooks; `jq . .claude/settings.json` parses.
- [x] 2.2 `delivery-guard.sh` on harness-shaped stdin: `gh issue create --title x --body y` denies, `git checkout -b x` and `git switch -c x` pass silently, `gh pr create --fill` on a non-issue HEAD denies. `gh issue create --help` also denies — the hook has no `--help` exemption and never had one; left as is and reported.
- [x] 2.3 Precedence of `ask` over `Bash(git *)` measured with a nested `claude -p` run against a scratch repository, with a control run lacking the `ask` array. Result recorded in the pull request.
- [x] 2.4 `git diff -U0` on `coordinator-guard.sh` shows comment lines only.
- [x] 2.5 `grep -rn "EnterWorktree\|ExitWorktree\|isolation: \"worktree\"" AGENTS.md .skills/repo-task-delivery/SKILL.md` leaves one hit: the one-rule paragraph naming `EnterWorktree` as the tool not to use, which the spec requires.
- [x] 2.6 Section size: 15 lines / 360 words before, 14 lines / 295 words after (-18% words).

## 3. Close out

- [x] 3.1 Archive this change on the issue branch, so one pull request carries the files, the spec update and the archive.
- [ ] 3.2 Open the pull request against `master` closing #387 and set the issue to In review. Do not merge.
