## 1. Rewrite the rules

- [x] 1.1 Rewrite `AGENTS.md` **# Branches and Worktrees** around the one invariant; drop the false "agent launched from the main checkout works in a directory of its own" bullet and replace it with `isolation: "worktree"`; drop the "no repo setting relaxes it" claim; re-date the measurement note to 2026-08-31 / Claude Code 2.1.251 and keep the re-measure instruction.
- [x] 1.2 Check `AGENTS.md` line 20 still points at a delegation rule that exists in the rewritten section; adjust only if it moved.
- [x] 1.3 Correct the same stale claims in `.claude/rules/repo-delivery.md`, keeping the paragraph the same length.
- [x] 1.4 Correct the stale 2026-08-16 paragraph in the `.claude/hooks/coordinator-guard.sh` header comment. Comment lines only — no executable line may change.

## 2. Verify

- [x] 2.1 Every claim in the rewritten text traces to the 2026-08-31 measurements on issue #382; nothing else is asserted.
- [x] 2.2 `bash -n .claude/hooks/coordinator-guard.sh` passes and `git diff` on that file shows comment lines only.
- [x] 2.3 Section size: 14 lines / 500 words before, 15 lines / 364 words after (-27% words).

## 3. Close out

- [x] 3.1 Archive this change on the issue branch, so one pull request carries the rewrite, the spec update and the archive.
- [ ] 3.2 Open the pull request against `master` closing #382 and set the issue to In review. Do not merge — the owner reviews process wording himself.
