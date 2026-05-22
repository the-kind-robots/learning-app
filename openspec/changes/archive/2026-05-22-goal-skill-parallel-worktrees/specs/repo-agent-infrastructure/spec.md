## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Backend port is overridable via environment variable
The backend server SHALL read its listening port from the `LEARNING_APP_PORT` environment variable when set, falling back to 8083 when absent.

#### Scenario: Backend starts with default port
- **WHEN** `LEARNING_APP_PORT` is not set in the environment
- **THEN** the backend listens on port 8083

#### Scenario: Backend starts on a custom port for a parallel worktree
- **WHEN** `LEARNING_APP_PORT=8183` is set in the environment
- **THEN** the backend listens on port 8183

### Requirement: Worktree server artifacts are gitignored
The repository SHALL ignore per-worktree server log and PID files so they do not appear as untracked noise.

#### Scenario: /goal creates backend log and PID files
- **WHEN** the `/goal` skill starts a background backend process in a worktree
- **THEN** `.worktree-backend.log` and `.worktree-backend.pid` are not shown by `git status`
