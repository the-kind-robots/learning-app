## REMOVED Requirements

### Requirement: Worktree server artifacts are gitignored
**Reason**: The log and PID files existed because `/goal` started a backend inside each worktree. Nothing writes them now: a worktree that needs the full stand is the main worktree, which has its own setup.

**Migration**: Drop `.worktree-backend.log` and `.worktree-backend.pid` from `.gitignore`.

## ADDED Requirements

### Requirement: Worktrees live where the agent tool puts them
Worktrees SHALL be created only through the agent's built-in mechanism, which keeps them under `.claude/worktrees/`. The repository SHALL NOT define a second location for them, and SHALL NOT carry permissions or ignore rules for locations of its own.

#### Scenario: Work needs isolation
- **WHEN** a task is isolated in a worktree
- **THEN** it is created by the built-in mechanism, and no worktree directory appears inside the repository tree

#### Scenario: Work needs the full stand
- **WHEN** a task touches sync, the dictionary, migrations, schema, or anything served through CouchDB or nginx
- **THEN** it runs in the main worktree, because a worktree isolates files while ports, CouchDB and nginx stay shared
