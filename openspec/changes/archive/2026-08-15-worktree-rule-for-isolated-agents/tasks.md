## 1. The rule

- [x] 1.1 Rewrite "Branches and Worktrees" in `AGENTS.md`: launch decision first, then the sequence
      for a session that can create worktrees freely, then the pinned-agent fallback with
      `EnterWorktree` excluded by name and the reason given
- [x] 1.2 Extend closeout in `AGENTS.md` to remove a nested worktree with its branch
- [x] 1.3 Mirror the launch decision and the fallback in `.claude/rules/repo-delivery.md`

## 2. The guard against committing one

- [x] 2.1 Add the nested worktree path to `.gitignore`
- [x] 2.2 Show that `git add -A` in the parent worktree no longer stages the inner checkout

## 3. Verification

- [x] 3.1 Deliver this very change by the fallback sequence as written, from an agent pinned to
      another worktree, and record whether any Bash call was refused
- [x] 3.2 Report the nested worktrees left from the earlier wave so they are removed with their
      branches
