# repo-agent-infrastructure Specification

## Purpose
TBD - created by archiving change keep-only-codex-agent-files. Update Purpose after archive.
## Requirements
### Requirement: Repository-owned agent infrastructure is Codex-only
The repository SHALL keep only Codex-related agent infrastructure files under version control.

#### Scenario: Shared repo agent files are reviewed
- **WHEN** repository-owned agent files are present in the worktree
- **THEN** the tracked shared agent infrastructure is limited to Codex-related files
- **AND** non-Codex agent ecosystems are not kept as repository-owned tracked files

#### Scenario: A developer uses local Claude or OpenCode tooling
- **WHEN** local non-Codex agent files are created in the working tree
- **THEN** git ignores those local artifacts
- **AND** repository-owned Codex files remain visible for tracking and review

### Requirement: Local non-Codex agent artifacts do not reappear as repo noise
The repository SHALL ignore local non-Codex agent artifacts so they do not keep showing up as worktree noise.

#### Scenario: A developer uses local Claude or OpenCode tooling
- **WHEN** local non-Codex agent files are created in the working tree
- **THEN** git ignores those local artifacts
- **AND** repository-owned Codex files remain visible for tracking and review

### Requirement: Backend port is overridable via environment variable
The backend server SHALL read its listening port from the `LEARNING_APP_PORT` environment variable when set, falling back to 8083 when absent.

#### Scenario: Backend starts with default port
- **WHEN** `LEARNING_APP_PORT` is not set in the environment
- **THEN** the backend listens on port 8083

#### Scenario: Backend starts on a custom port for a parallel worktree
- **WHEN** `LEARNING_APP_PORT=8183` is set in the environment
- **THEN** the backend listens on port 8183

### Requirement: Worktrees live where the agent tool puts them
Worktrees SHALL be created only through the agent's built-in mechanism, which keeps them under `.claude/worktrees/`. The repository SHALL NOT define a second location for them, and SHALL NOT carry permissions or ignore rules for locations of its own.

#### Scenario: Work needs isolation
- **WHEN** a task is isolated in a worktree
- **THEN** it is created by the built-in mechanism, and no worktree directory appears inside the repository tree

#### Scenario: Work needs the full stand
- **WHEN** a task touches sync, the dictionary, migrations, schema, or anything served through CouchDB or nginx
- **THEN** it runs in the main worktree, because a worktree isolates files while ports, CouchDB and nginx stay shared

