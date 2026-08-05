# repo-delivery-workflow Specification

## Purpose
TBD - created by archiving change improve-tracked-workflow-and-project-cdp-browser-handling. Update Purpose after archive.
## Requirements
### Requirement: Likely repository changes default to tracked delivery
The repository workflow SHALL treat newly reported problems or proposed changes that are likely to become committed repository edits as tracked work by default.

#### Scenario: Warning likely leading to a fix starts tracked workflow
- **WHEN** a user reports a warning, error, broken behavior, or proposed improvement that is likely to require a repo commit
- **THEN** the workflow starts issue + OpenSpec tracking before repository code edits begin
- **AND** the assistant only skips tracked delivery when the user explicitly asks to avoid it

### Requirement: Task boundaries reset after delivery closeout
The repository workflow SHALL treat repo work that starts after issue/PR closeout as a new tracked task by default unless it is clearly just final closeout work for the finished task.

#### Scenario: New repo scope starts after the previous task was closed
- **WHEN** the previous tracked task has already been merged or closed
- **AND** the user switches to a new non-trivial repo scope
- **THEN** the workflow starts a new tracked issue/change by default
- **AND** it does not silently continue on the previous issue or branch unless the user is clearly finishing only closeout tail work

### Requirement: Optional GitHub Project fields do not block start-work
The GitHub Project start-work workflow SHALL continue when an optional configured single-select field is absent on the target project.

#### Scenario: Project has Status but no Category
- **WHEN** the start-work workflow runs against a project that exposes `Status` but not `Category`
- **THEN** the issue and project item are still created successfully
- **AND** missing optional fields are skipped with a warning instead of failing the workflow

### Requirement: Visual UI work favors perceptual stability
The repository workflow SHALL treat unnecessary visible UI jumps, jerks, or geometry shifts as quality regressions during visual interaction work.

#### Scenario: Verifying a UI change that affects layout or transitions
- **WHEN** a task changes interactive UI layout, swapping, focus flow, keyboard flow, or footer/panel geometry
- **THEN** the expected quality bar includes that the screen remains perceptually stable instead of visibly jumping or jerking
- **AND** verification uses real browser evidence such as measured geometry, traces, or equivalent browser-level checks rather than DOM shape alone

### Requirement: Repo-owned verification tooling is repaired before fallback
The repository workflow SHALL treat failures in repo-owned verification tooling as task-relevant bugs to repair before relying on alternate verification stacks by default.

#### Scenario: Preferred CDP workflow is flaky during a browser/UI task
- **WHEN** a browser or UI task is supposed to be verified through the repository's preferred CDP/browser workflow
- **AND** that workflow fails because it attaches to the wrong target, loses route state, races setup, or otherwise gives untrustworthy results
- **THEN** the workflow first repairs the repo-owned tooling or wrapper as part of the tracked work
- **AND** alternate browser tooling is not used as the default workaround
- **AND** a fallback path is used only when the user explicitly allows it or the assistant clearly states that repair of the preferred tooling is blocked

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

### Requirement: Merge order is machine-enforced
A PR declaring `Depends-on: #N` SHALL NOT be mergeable while any referenced issue or pull request is open; the enforcement SHALL come from branch protection, not from convention. A missing reference SHALL fail the same way, so a typo cannot silently unlock a merge.

#### Scenario: The blocker is open
- **WHEN** a PR body contains `Depends-on: #N` and node N is open
- **THEN** the required check fails and the merge button is disabled

#### Scenario: The blocker lands
- **WHEN** a push to master closes node N
- **THEN** the dependent's check is re-run automatically and turns green

