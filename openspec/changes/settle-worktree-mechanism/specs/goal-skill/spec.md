## REMOVED Requirements

### Requirement: /goal skill provides autonomous task delivery in an isolated worktree
**Reason**: Worktree creation is provided by the agent tool itself (`EnterWorktree` / `ExitWorktree`, kept under `.claude/worktrees/`), so the skill's main job is now built in. Its other job — starting the backend and shadow-cljs on dedicated ports — only applies to the main worktree, which already has the stand configured; the ports, CouchDB and nginx are shared and a worktree does not isolate them.

**Migration**: Use the built-in mechanism for worktrees and `repo-task-delivery` for the workflow. Tasks needing the full stand run in the main worktree.
