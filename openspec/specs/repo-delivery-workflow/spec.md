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

### Requirement: A required check reports on every pull request
Every PR SHALL receive a verdict from each required check — pass, fail, or skipped — regardless of which paths it touches. A required check that cannot start is a broken merge pipeline, not a passing one.

#### Scenario: A PR outside the tested paths
- **WHEN** a PR touches no path the heavy job cares about
- **THEN** the check reports skipped and branch protection is satisfied

#### Scenario: A PR inside the tested paths
- **WHEN** a PR touches the application or its build inputs
- **THEN** the full suite runs and its verdict gates the merge

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

Tracked work SHALL happen on a branch created from its issue, including when the work is isolated in a worktree. The repository SHALL record, for each situation it puts agents in, an order that satisfies both the branch rule and the worktree rule and that runs to a pushed branch without a refused command. Where an agent cannot create a worktree beside its own, the recorded order SHALL say so and give the one that works, naming the tool that must not be used and why. A session that coordinates tracked work SHALL delegate repository edits rather than making them and SHALL stay in the main checkout, because an agent launched from a worktree inherits that pin and can no longer place its own work in a worktree of its own.

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

### Requirement: Filing an issue stays within a small share of the API budget

The tracked-delivery workflow SHALL file an issue without consuming a disproportionate share
of the hourly GraphQL budget, so that a batch of backlog work can be filed in one sitting.

Lookups the workflow performs SHALL request only the data the workflow reads. Matching a board
item needs its id, title, type and issue number; resolving a field needs that field's id and
the id of the option being set. Requesting every field value of every board item, or every
option of every field, is not permitted merely because a convenience command offers it.

Reducing the cost SHALL NOT cost behaviour: a same-titled draft item on the board is still
found and converted into the issue rather than duplicated.

#### Scenario: Filing twenty issues in a row

- **WHEN** twenty issues are filed one after another through the start-work script
- **THEN** every one of them is created, placed on the board, and given Status and Priority
- **AND** the hourly GraphQL budget is not exhausted

#### Scenario: A same-titled draft is still reused

- **WHEN** the board holds a draft item whose title matches the issue being filed
- **THEN** that draft is converted into the issue
- **AND** no second board item is created

#### Scenario: The cost is visible

- **WHEN** the script finishes a run
- **THEN** it reports what that run cost against the GraphQL budget

