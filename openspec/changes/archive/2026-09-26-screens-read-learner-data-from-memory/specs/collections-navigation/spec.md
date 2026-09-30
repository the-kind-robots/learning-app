## MODIFIED Requirements

### Requirement: Opening the themes screen switches to it at once
The system SHALL switch to the themes screen the moment it is opened, with its tiles. A collection the learner creates or deletes on the screen SHALL be shown or taken off at once; one that arrives from elsewhere SHALL show the next time the screen is opened.

#### Scenario: First open shows loading before the collections
- **WHEN** the user opens the themes screen
- **THEN** the screen is on display with its tiles, with no loading state

#### Scenario: A post-pull reload keeps the current content
- **WHEN** the themes screen is on display and a sync pull brings a collection
- **THEN** the tiles on display do not change
- **AND** the collection is among the tiles once the screen is opened again

### Requirement: The themes screen reads collection documents only
**Reason**: Opening the themes screen reads no document at all; it answers from the learner's data in memory (`learner-data-memory`).
**Migration**: None — the requirement that no screen reads storage on entry replaces it.
