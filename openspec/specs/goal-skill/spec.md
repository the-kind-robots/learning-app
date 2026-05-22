# goal-skill Specification

## Purpose
TBD - created by syncing change goal-skill-parallel-worktrees. Update Purpose after archive.

## Requirements

### Requirement: /goal skill provides autonomous task delivery in an isolated worktree
The repository SHALL provide a `/goal` slash command that runs the full `repo-task-delivery` workflow inside a dedicated git worktree with dedicated ports, pausing only for a final review gate.

#### Scenario: Developer starts a task with /goal
- **WHEN** a developer invokes `/goal <task description>` in a Claude Code session
- **THEN** the skill creates or detects a git worktree for the task
- **AND** claims an unused port slot from the registry
- **AND** starts the backend server on the allocated port
- **AND** runs the full `repo-task-delivery` workflow without further interruptions
- **AND** stops with a "READY FOR REVIEW" announcement when the PR is ready

#### Scenario: /goal asks at most one clarifying question
- **WHEN** the task description is clear enough to determine scope
- **THEN** the skill proceeds without asking any questions
- **WHEN** the task description is genuinely ambiguous
- **THEN** the skill asks exactly one focused question before starting

### Requirement: Port slot allocation is atomic and crash-safe
The slot registry SHALL assign each `/goal` session a unique port slot such that no two concurrent sessions share ports, and stale slots from crashed sessions are recovered automatically.

#### Scenario: Two /goal sessions start concurrently
- **WHEN** two Claude Code sessions invoke `/goal` at the same time
- **THEN** each session receives a distinct slot number
- **AND** their backend ports differ by at least 100

#### Scenario: Slot from a crashed session is recovered
- **WHEN** a previous `/goal` session ended without releasing its slot
- **AND** its worktree has since been removed from `git worktree list`
- **THEN** the next `claim-slot.sh` call reclaims that slot

### Requirement: Port slots are released on task completion
The skill SHALL release the claimed port slot and stop background processes when the task is merged or explicitly abandoned.

#### Scenario: Task merges successfully
- **WHEN** the user approves the PR and it is merged
- **THEN** the background backend process is stopped
- **AND** the slot directory is removed from `.git/worktree-slots/`

### Requirement: Nginx vhost config is generated per slot for browser review
The skill SHALL produce a ready-to-activate nginx config for each slot so the user can access the worktree's running app via `https://sprecha.local:<4430+N>/`.

#### Scenario: Developer wants to preview a worktree in the browser
- **WHEN** the skill reaches the review gate
- **THEN** it prints the nginx vhost config for the slot
- **AND** provides the exact commands to activate it (write + `sudo nginx -s reload`)
- **AND** the backend is already running on the slot's port so the app is immediately accessible after reload
