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
