## MODIFIED Requirements

### Requirement: A branch for tracked work is linked to its issue

Tracked work SHALL happen on a branch created from its issue, including when the work is isolated in a worktree. The repository SHALL record, for each situation it puts agents in, an order that satisfies both the branch rule and the worktree rule and that runs to a pushed branch without a refused command. Where an agent cannot create a worktree beside its own, the recorded order SHALL say so and give the one that works, naming the tool that must not be used and why. A session that coordinates tracked work SHALL delegate repository edits rather than making them and SHALL stay in the main checkout, because an agent launched from a worktree inherits that pin and can no longer place its own work in a worktree of its own.

Where the coordinating session runs in the background, the repository SHALL record that no third option exists, next to the delegation rule itself rather than leaving it to be discovered when a write is refused. The coordinator stays in the main checkout, and the harness refuses a background session's writes to the shared checkout; together those leave exactly one destination, so every delegated edit SHALL be placed in a worktree on the issue branch. An agent delegated such an edit SHALL be launched with worktree isolation, or the coordinator SHALL enter a worktree before launching it; otherwise the agent stops on its first write having done nothing.

#### Scenario: Work is isolated in a worktree

- **WHEN** a task needs a worktree
- **THEN** the issue branch is created first and the worktree is placed on that branch, so the issue keeps its development link

#### Scenario: An agent is launched pinned to a worktree

- **WHEN** work that must land its own issue branch is delegated to an agent
- **THEN** that agent is launched without worktree isolation, because a pinned agent cannot reach a worktree created beside its own

#### Scenario: A pinned agent must deliver anyway

- **WHEN** an agent already pinned to a worktree has to land a different issue branch
- **THEN** it creates the issue branch first, adds a linked worktree inside its own worktree, works there with plain directory changes, and reaches a pushed branch with no refused command

#### Scenario: The session that coordinates the work

- **WHEN** a coordinating session needs a repository edit
- **THEN** it delegates that edit and stays in the main checkout, so the agent it launches can create its own `.claude/worktrees/<slug>` from the issue branch and work there

#### Scenario: A background coordinating session delegates an edit

- **WHEN** the coordinating session runs in the background and delegates a repository edit
- **THEN** the edit is placed in a worktree, because writes to the shared checkout are refused for a background session and the coordinator does not make the edit itself
- **AND** that constraint is already stated with the delegation rule, so it is not first learned from the refusal
