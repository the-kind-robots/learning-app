# repo-agent-infrastructure Specification

## Purpose
TBD - created by archiving change keep-only-codex-agent-files. Update Purpose after archive.
## Requirements
### Requirement: Backend port is overridable via environment variable
The backend server SHALL read its listening port from the `LEARNING_APP_PORT` environment variable when set, falling back to 8083 when absent.

#### Scenario: Backend starts with default port
- **WHEN** `LEARNING_APP_PORT` is not set in the environment
- **THEN** the backend listens on port 8083

#### Scenario: Backend starts on a custom port for a parallel worktree
- **WHEN** `LEARNING_APP_PORT=8183` is set in the environment
- **THEN** the backend listens on port 8183

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

### Requirement: Repository-owned agent infrastructure covers the agents the repo relies on

The repository SHALL keep under version control the agent infrastructure its own rules
depend on, for every agent used to work on it — not for one agent only. Files that record
a developer's local preferences SHALL stay ignored.

The line is what the file is for, not which tool reads it: a hook that enforces the
delivery flow, a rules file that carries it, and the skills both agents share are
repository infrastructure and are tracked; a personal settings override is local and is
ignored.

#### Scenario: Shared repo agent files are reviewed

- **WHEN** repository-owned agent files are present in the worktree
- **THEN** the tracked set covers the rules, hooks, settings and skills the repo's own
  workflow depends on
- **AND** each is reviewable and reaches every worktree through git

#### Scenario: A developer keeps local tooling preferences

- **WHEN** local, developer-specific agent files are created in the working tree
- **THEN** git ignores those local artifacts
- **AND** repository-owned agent infrastructure remains visible for tracking and review

### Requirement: Skill invocations use a path that resolves in a worktree

Documented invocations of repository skills SHALL use a path present in every checkout,
including worktrees. A path that exists only in the main checkout SHALL NOT be the
documented one.

#### Scenario: A skill is invoked from a worktree

- **WHEN** an agent follows a documented command from a skill or from the repo agent rules
  while working in a worktree
- **THEN** the path resolves and the script runs

#### Scenario: A path outside the repository

- **WHEN** an invocation refers to a user-global installation rather than a repository
  file
- **THEN** it is left as it is, since it is not a repository path

