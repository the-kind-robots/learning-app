## MODIFIED Requirements

### Requirement: A branch for tracked work is linked to its issue

Tracked work SHALL happen on a branch created from its issue, including when the work is isolated in a worktree. The repository SHALL record, for each situation it puts agents in, an order that satisfies both the branch rule and the worktree rule and that runs to a pushed branch without a refused command. Where an agent cannot create a worktree beside its own, the recorded order SHALL say so and give the one that works, naming the tool that must not be used and why.

#### Scenario: Work is isolated in a worktree

- **WHEN** a task needs a worktree
- **THEN** the issue branch is created first and the worktree is placed on that branch, so the issue keeps its development link

#### Scenario: An agent is launched pinned to a worktree

- **WHEN** work that must land its own issue branch is delegated to an agent
- **THEN** that agent is launched without worktree isolation, because a pinned agent cannot reach a worktree created beside its own

#### Scenario: A pinned agent must deliver anyway

- **WHEN** an agent already pinned to a worktree has to land a different issue branch
- **THEN** it creates the issue branch first, adds a linked worktree inside its own worktree, works there with plain directory changes, and reaches a pushed branch with no refused command
