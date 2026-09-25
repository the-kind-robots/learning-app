## MODIFIED Requirements

### Requirement: Opening the themes screen switches to it at once
The system SHALL switch to the themes screen the moment it is opened. It SHALL show a loading state there only while the learner's data is not yet in memory, and its collections once it is. A change to the collections while the screen is open SHALL replace the tiles in place, with no loading state in between.

#### Scenario: First open shows loading before the collections
- **WHEN** the user opens the themes screen before the learner's data is in memory
- **THEN** the screen is on display with a loading state
- **AND** the collections replace the loading state once they are available

#### Scenario: A post-pull reload keeps the current content
- **WHEN** the themes screen is on display and a sync pull brings a collection
- **THEN** the tiles are replaced in place
- **AND** no loading state is shown in between

## REMOVED Requirements

### Requirement: The themes screen reads collection documents only
**Reason**: Opening the themes screen reads no document at all; it answers from the learner's data in memory (`learner-data-memory`).
**Migration**: None — the requirement that no screen reads storage on entry replaces it.
