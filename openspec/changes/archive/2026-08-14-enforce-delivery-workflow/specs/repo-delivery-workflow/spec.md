# repo-delivery-workflow Delta

## ADDED Requirements

### Requirement: The delivery rules are present in every session

The repository SHALL state the delivery flow in files the agent loads at session start,
not only inside a skill that must first be chosen. The statement SHALL name the
entrypoint skill, the sequence, and the cases where the flow does not apply.

#### Scenario: A session begins

- **WHEN** an agent session starts in this repository, in the main checkout or in a
  worktree
- **THEN** the delivery flow and its entrypoint are already in context, without any file
  being read first

#### Scenario: A task arrives that will end in a commit

- **WHEN** the user reports a bug or proposes a change likely to become a committed edit
- **THEN** the agent enters the flow through the entrypoint skill rather than assembling
  the GitHub and OpenSpec steps by hand

### Requirement: Commands that bypass tracked delivery are refused

The repository SHALL refuse the commands that create work outside the board. A raw issue
creation SHALL be denied. A pull request SHALL be denied from a branch that carries no
issue number, and allowed from a branch created for an issue. Each refusal SHALL name the
command to use instead.

#### Scenario: Issue created by hand

- **WHEN** an agent runs a raw issue-creation command
- **THEN** the call is denied and the refusal names the workflow script that creates the
  issue, places it on the board and sets its fields

#### Scenario: Pull request from an untracked branch

- **WHEN** an agent opens a pull request from a branch with no issue number
- **THEN** the call is denied, because a pull request with no issue behind it is work the
  board cannot see

#### Scenario: Pull request from an issue branch

- **WHEN** the branch was created for an issue
- **THEN** opening the pull request by hand is allowed, which is what a dirty worktree
  requires anyway

#### Scenario: The owner asks for the raw command

- **WHEN** the user explicitly wants the bypassed command
- **THEN** an explicit prefix downgrades the refusal to an approval prompt, so the human
  confirms rather than the agent deciding alone

### Requirement: A branch for tracked work is linked to its issue

Tracked work SHALL happen on a branch created from its issue, including when the work is
isolated in a worktree. The repository SHALL record the order that satisfies both the
branch rule and the worktree rule.

#### Scenario: Work is isolated in a worktree

- **WHEN** a task needs a worktree
- **THEN** the issue branch is created first and the worktree is placed on that branch,
  so the issue keeps its development link
