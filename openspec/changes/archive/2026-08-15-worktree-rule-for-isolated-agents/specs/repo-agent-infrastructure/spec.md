## MODIFIED Requirements

### Requirement: Worktrees live where the agent tool puts them
Worktrees SHALL be created only through the agent's built-in mechanism, which keeps them under `.claude/worktrees/`, except where that mechanism cannot reach the branch the work must land on: an agent pinned to a worktree SHALL create its linked worktree inside the worktree it was given, since a path outside it is refused. The repository SHALL NOT define a second location for ordinary work, and the only ignore rule it SHALL carry for worktrees is the one that keeps such a nested worktree from being committed, because staging everything in the parent records the inner checkout as an embedded repository.

#### Scenario: Work needs isolation
- **WHEN** a task is isolated in a worktree
- **THEN** it is created by the built-in mechanism, and no worktree directory appears inside the repository tree

#### Scenario: Work needs the full stand
- **WHEN** a task touches sync, the dictionary, migrations, schema, or anything served through CouchDB or nginx
- **THEN** it runs in the main worktree, because a worktree isolates files while ports, CouchDB and nginx stay shared

#### Scenario: A pinned agent creates the only worktree it can use
- **WHEN** an agent pinned to a worktree adds a linked worktree inside it
- **THEN** staging everything in the parent worktree cannot commit that checkout, and closeout removes it together with the branch
