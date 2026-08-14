# repo-agent-infrastructure Delta

## MODIFIED Requirements

### Requirement: Repository-owned agent infrastructure covers the agents the repo relies on
The repository SHALL keep under version control the agent infrastructure that its own rules depend on, for every agent used to work on it — not for one agent only. Files that merely record a developer's local preferences SHALL stay ignored.

The distinction is what the file is for, not which tool reads it: a hook that enforces the delivery flow, a rules file that carries it, and the skills both agents share are repository infrastructure and are tracked; a personal settings override is local and is ignored.

#### Scenario: Shared repo agent files are reviewed
- **WHEN** repository-owned agent files are present in the worktree
- **THEN** the tracked set covers the rules, hooks, settings and skills the repo's own workflow depends on
- **AND** each is reviewable and reaches every worktree through git

#### Scenario: A developer keeps local tooling preferences
- **WHEN** local, developer-specific agent files are created in the working tree
- **THEN** git ignores those local artifacts
- **AND** repository-owned agent infrastructure remains visible for tracking and review

## ADDED Requirements

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
