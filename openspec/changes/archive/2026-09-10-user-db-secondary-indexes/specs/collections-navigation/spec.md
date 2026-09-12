## ADDED Requirements

### Requirement: Opening the themes screen switches to it at once
The system SHALL switch to the themes screen the moment it is opened and SHALL show a loading state there until its collections are available. A reload of the screen that is already open (after a sync pull) SHALL keep the current collections on screen until the new ones arrive, with no loading state in between.

#### Scenario: First open shows loading before the collections
- **WHEN** the user opens the themes screen
- **THEN** the screen is on display with a loading state before its collections have been read
- **AND** the collections replace the loading state once they are available

#### Scenario: A post-pull reload keeps the current content
- **WHEN** the themes screen is on display and a sync pull completes
- **THEN** the current collections stay on screen until the reloaded ones replace them
- **AND** no loading state is shown in between
