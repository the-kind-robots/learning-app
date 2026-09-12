## ADDED Requirements

### Requirement: A pull that writes no document leaves the current screen untouched
A sync pass SHALL report how many documents it pulled and pushed. The current screen SHALL be reloaded — and a waiting pairing dialog checked — only when the pass pulled at least one document; a pass that pulled nothing SHALL change nothing on screen.

#### Scenario: Idle poke with nothing new
- **WHEN** the poke socket reconnects or a poke arrives and the pull writes no document
- **THEN** the current screen is not reloaded and does not re-render

#### Scenario: Pull brings new documents
- **WHEN** a pull writes at least one document
- **THEN** the current screen reloads its data and reflects them

### Requirement: Route entry does not pull again within 30 s of the last completed pass
A navigation SHALL run no sync pass when a pass completed less than 30 s earlier and nothing was written locally since. A poke from the push socket SHALL always run a pass.

#### Scenario: Navigation soon after a pass
- **WHEN** the user opens another screen within 30 s of a completed pass and has written nothing locally since
- **THEN** no pass runs

#### Scenario: Poke within the interval
- **WHEN** a poke arrives within 30 s of a completed pass
- **THEN** a pass runs
