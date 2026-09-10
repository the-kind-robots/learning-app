# repo-delivery-workflow Delta

## MODIFIED Requirements

### Requirement: Likely repository changes default to tracked delivery
The repository workflow SHALL treat newly reported problems or proposed changes that are likely to become committed repository edits as tracked work by default.

Tracked delivery SHALL mean an issue on the board, a branch created from that issue, and a pull request. Those SHALL apply to every tracked edit without exception.

An OpenSpec change SHALL be required only for work that alters product behaviour a reader can verify afterwards without reading the diff. The decision SHALL be stated as a test rather than a list of subsystems: name the behaviour the work alters that someone could check afterwards; when such a behaviour can be named the work SHALL carry an OpenSpec change, and when none can be named the work SHALL NOT carry one, because there is no requirement to state.

Work on the repository's own process — agent rules, delivery hooks, skills, scripts, CI configuration, and documentation about how work is delivered — SHALL NOT be required to carry an OpenSpec change. A normative requirement SHALL NOT be manufactured so that `openspec validate` accepts a change; a change with no delta is a change that SHALL NOT have been created.

The test SHALL live in the files loaded at session start, so it is in context before the decision is made, and process rules SHALL be recorded there rather than in a spec.

#### Scenario: Warning likely leading to a fix starts tracked workflow
- **WHEN** a user reports a warning, error, broken behavior, or proposed improvement that is likely to require a repo commit
- **THEN** the workflow starts issue tracking before repository code edits begin
- **AND** an OpenSpec change is started as well when the fix alters verifiable product behaviour
- **AND** the assistant only skips tracked delivery when the user explicitly asks to avoid it

#### Scenario: The work changes what the product does
- **WHEN** the work alters behaviour a reader could check afterwards without reading the diff
- **THEN** it carries an OpenSpec change with a delta, archived on the delivery branch before the pull request

#### Scenario: The work changes how the repository is worked
- **WHEN** the work touches only agent rules, hooks, skills, scripts, CI configuration or delivery documentation
- **THEN** it carries no OpenSpec change, and it still carries its issue, its branch from that issue, and its pull request
- **AND** any process rule it establishes is written where the session already loads it, not as a spec requirement
