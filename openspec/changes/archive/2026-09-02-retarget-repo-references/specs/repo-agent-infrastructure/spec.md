## ADDED Requirements

### Requirement: Agent infrastructure names the repository's current coordinate

Repository-owned agent infrastructure that spells out an `owner/repo` coordinate SHALL name the repository's current one. This covers the read-only command allowlist, documented `gh` recipes, skill configuration defaults and the text of guard refusals.

The allowlist is the case that costs something: it matches invocations as exact strings, so an entry naming a former coordinate is dead weight — the command it was meant to pre-approve prompts instead. A redirect at the forge hides the breakage rather than repairing it.

A coordinate that names something the transfer did not move SHALL be left alone. The project board is owned separately from the repository, so its owner and number do not follow a repository transfer.

Archived changes SHALL be left as written. They record what was true when they were archived.

#### Scenario: The repository is transferred

- **WHEN** the repository moves to a new owner
- **THEN** the allowlist, the documented recipes, the skill config defaults and the guard refusal text name the new coordinate
- **AND** a pre-approved read-only command still matches after the move

#### Scenario: A coordinate the transfer did not move

- **WHEN** agent infrastructure names the project board's owner and number
- **THEN** those are left unchanged, because the board did not move with the repository

#### Scenario: An archived change names the old coordinate

- **WHEN** an archived change refers to the repository under its former name
- **THEN** it is left as written, because it is a record of what was true then
