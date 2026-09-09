## MODIFIED Requirements

### Requirement: A branch for tracked work is linked to its issue

Tracked work SHALL happen on a branch created from its issue, including when the work is isolated in a worktree. The repository SHALL record, for each situation it puts agents in, an order that satisfies both the branch rule and the worktree rule and that runs to a pushed branch without a refused command. That record SHALL rest on one invariant — everything under an agent's own worktree root is reachable to it, and nothing outside that root is — and SHALL name the tool that must not be used and why. A session that coordinates tracked work SHALL delegate repository edits rather than making them and SHALL stay in the main checkout.

#### Scenario: Work is isolated in a worktree

- **WHEN** a task needs a worktree
- **THEN** the issue branch is created first and the worktree is placed on that branch, so the issue keeps its development link

#### Scenario: Work is delegated to an executor

- **WHEN** work that must land its own issue branch is delegated to an agent
- **THEN** that agent is launched with worktree isolation, because an agent launched without it can neither enter a worktree nor write into the repository through the editor tools

#### Scenario: A pinned agent must deliver anyway

- **WHEN** an agent already pinned to a worktree has to land a different issue branch
- **THEN** it creates the issue branch first, adds a linked worktree inside its own worktree, works there with plain directory changes, and reaches a pushed branch with no refused command

#### Scenario: The session that coordinates the work

- **WHEN** a coordinating session needs a repository edit
- **THEN** it delegates that edit and stays in the main checkout, and the agent it launches is given a worktree of its own
