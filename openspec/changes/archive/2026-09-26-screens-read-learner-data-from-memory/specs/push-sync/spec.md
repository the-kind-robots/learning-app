## MODIFIED Requirements

### Requirement: A pull that writes no document leaves the current screen untouched
A sync pass SHALL report how many documents it pulled and pushed. The documents a pass pulls SHALL reach the current screen through the learner's data in memory; a waiting pairing dialog SHALL be checked only when the pass pulled at least one document. A pass that pulled nothing SHALL change nothing on screen.

#### Scenario: Idle poke with nothing new
- **WHEN** the poke socket reconnects or a poke arrives and the pull writes no document
- **THEN** the current screen does not re-render

#### Scenario: Pull brings new documents
- **WHEN** a pull writes at least one document
- **THEN** the current screen reflects them
