## MODIFIED Requirements

### Requirement: A pull that writes no document leaves the current screen untouched
A sync pass SHALL report how many documents it pulled and pushed. The documents a pass pulls SHALL reach the learner's data in memory and SHALL show on the next screen opened; no pass SHALL change the screen on display. A waiting pairing dialog SHALL be checked only when the pass pulled at least one document.

#### Scenario: Idle poke with nothing new
- **WHEN** the poke socket reconnects or a poke arrives and the pull writes no document
- **THEN** the current screen does not change

#### Scenario: Pull brings new documents
- **WHEN** a pull writes at least one document
- **THEN** the current screen does not change
- **AND** the next screen opened shows them
