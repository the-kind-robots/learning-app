## REMOVED Requirements

### Requirement: /goal skill provides autonomous task delivery in an isolated worktree
**Reason**: Worktree creation is provided by the agent tool itself (`EnterWorktree` / `ExitWorktree`, kept under `.claude/worktrees/`), so the skill's main job is now built in. Its other job — starting the backend and shadow-cljs on dedicated ports — only applies to the main worktree, which already has the stand configured; the ports, CouchDB and nginx are shared and a worktree does not isolate them.

**Migration**: Use the built-in mechanism for worktrees and `repo-task-delivery` for the workflow. Tasks needing the full stand run in the main worktree.

### Requirement: Port slot allocation is atomic and crash-safe
**Reason**: Nothing claims a slot any more. The registry lived in `.git/worktree-slots/`, one of the four competing worktree locations this change removes.

**Migration**: None. Running a second full stand in parallel is a separate question, tracked on its own.

### Requirement: Port slots are released on task completion
**Reason**: Follows the registry it released into.

**Migration**: None. Branch and worktree cleanup at merge is now a requirement of `repo-delivery-workflow`.

### Requirement: Nginx vhost config is generated per slot for browser review
**Reason**: Generated a vhost per slot on `https://sprecha.local:<4430+N>/`, which only the deleted skill produced. It is also the wrong shape: a browser reaches `http://<name>.localhost:<port>` with no vhost, no certificate and no hosts entry, and still counts as a secure context, so service workers and `Secure` cookies work — measured on the dev machine.

**Migration**: None here. A per-worktree stand needs the backend to proxy `/db/` in development; tracked separately.
