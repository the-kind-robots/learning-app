## 1. OpenSpec

- [x] 1.1 Add the `repo-agent-infrastructure` capability spec for Codex-only repository-owned agent files.
- [x] 1.2 Describe the cleanup scope and ownership rules in the change proposal and design.

## 2. Repository Cleanup

- [x] 2.1 Remove tracked `.claude/` files from the repository.
- [x] 2.2 Remove tracked `.opencode/` files from the repository.
- [x] 2.3 Keep only repository-owned Codex files under `.codex/` and `codex/`.

## 3. Guardrails

- [x] 3.1 Update `.gitignore` so local non-Codex agent artifacts do not keep reappearing in the worktree.

## 4. Verify

- [x] 4.1 Verify the remaining agent files in the repository are Codex-related only.
- [ ] 4.2 Verify the worktree no longer reports tracked Claude/OpenCode files after cleanup.
