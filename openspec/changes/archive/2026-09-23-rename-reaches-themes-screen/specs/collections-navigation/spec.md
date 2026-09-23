## MODIFIED Requirements

### Requirement: A collection is renamed on the home screen heading
The system SHALL let the user rename the active collection by editing the home screen's heading. A name another collection already carries — trimmed, case-insensitive — SHALL be refused the way a blank one is: the heading shows the current name again and no document is written. A rename SHALL reach whichever screen is on display when its write lands, so a themes screen opened from the heading while the name is still being written shows the new name.

#### Scenario: Rename updates the stored name
- **WHEN** the user edits the home screen heading and submits a non-blank value no other collection carries
- **THEN** the collection document is updated with the new name
- **AND** the heading and the collection's tile show it

#### Scenario: Rename to a taken name is refused
- **WHEN** the user submits a name another collection carries
- **THEN** the heading shows the current name again
- **AND** the collection document is unchanged

#### Scenario: The themes screen opened from the heading shows the new name
- **WHEN** the user types a new name into the heading and, with the caret still in it, taps the collections icon
- **THEN** the themes screen shows the collection under the new name
- **AND** no tile carries the old name
