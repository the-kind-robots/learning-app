## ADDED Requirements

### Requirement: Branches are created through the issue
A branch for tracked work SHALL be created with `gh issue develop <number> --checkout`, which registers it against the issue on GitHub. Tracked work SHALL NOT start from a branch made with `git checkout -b` or `git worktree add -b`.

#### Scenario: Work starts
- **WHEN** an issue exists and implementation is about to begin
- **THEN** the branch is created from that issue and appears in its development links

#### Scenario: A branch appears without an issue
- **WHEN** a branch exists that no issue points to
- **THEN** it is treated as untracked work: either an issue is created for it, or the branch is removed

### Requirement: Delivery ends with cleanup
Closeout SHALL include deleting the delivered branch locally and on the remote, and removing the worktree if the work ran in one.

#### Scenario: A pull request is merged
- **WHEN** the pull request for tracked work is merged
- **THEN** the branch is gone from both sides and no worktree is left behind for it
