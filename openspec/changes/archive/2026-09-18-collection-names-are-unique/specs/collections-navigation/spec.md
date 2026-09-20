## MODIFIED Requirements

### Requirement: Rename collection updates collection name
The system SHALL allow the user to rename a named collection via inline editing. A name another collection already carries — trimmed, case-insensitive — SHALL be refused the way a blank one is: the heading shows the current name again and no document is written.

#### Scenario: Rename updates stored collection name
- **WHEN** the user edits the card name and submits a non-blank value no other collection carries
- **THEN** the collection document is updated with the new name
- **AND** the card reflects the new name

#### Scenario: Rename to a taken name is refused
- **WHEN** the user submits a name another collection carries
- **THEN** the heading shows the current name again
- **AND** the collection document is unchanged
