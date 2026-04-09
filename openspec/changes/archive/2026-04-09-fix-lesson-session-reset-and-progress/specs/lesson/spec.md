## ADDED Requirements

### Requirement: Lesson entry starts a fresh session
The system SHALL start a new lesson session when the user enters the lesson flow from the UI, rather than silently reusing an older persisted lesson document.

#### Scenario: Re-entering lesson after leaving an unfinished session
- **WHEN** a user enters the lesson flow while a previous local lesson document still exists
- **THEN** the system creates a new lesson session from the current vocabulary state
- **AND** the newly rendered lesson does not reuse the stale persisted session

### Requirement: Word-trial answers record review progress
The system SHALL record review data for word-trial answers so retention-based progress updates are reflected after lesson activity.

#### Scenario: Answering a word trial
- **WHEN** a user submits an answer for a word trial
- **THEN** the system records a review for that word using the answer result
- **AND** subsequent progress calculations can observe the updated review data

#### Scenario: Answering an example trial
- **WHEN** a user submits an answer for an example trial
- **THEN** the system does not create a vocabulary review document for that answer
