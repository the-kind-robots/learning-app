## MODIFIED Requirements

### Requirement: Lesson exit replaces browser history
The system SHALL replace the current browser history entry when a learner exits a lesson through completion or cancellation.

#### Scenario: Finished lesson exit
- **WHEN** a learner completes the final lesson trial and exits the lesson flow
- **THEN** the app navigates to the home page
- **AND** the navigation replaces the current history entry so the lesson page is not restored by pressing browser Back

#### Scenario: Cancelled lesson exit
- **WHEN** a learner cancels an active lesson
- **THEN** the app navigates to the home page
- **AND** the navigation replaces the current history entry so the cancelled lesson screen is not restored by pressing browser Back
