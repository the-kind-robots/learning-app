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

### Requirement: Local non-Codex agent artifacts do not reappear as repo noise
The repository SHALL ignore local non-Codex agent artifacts so they do not keep showing up as worktree noise.

#### Scenario: A developer uses local Claude or OpenCode tooling
- **WHEN** local non-Codex agent files are created in the working tree
- **THEN** git ignores those local artifacts
- **AND** repository-owned Codex files remain visible for tracking and review

