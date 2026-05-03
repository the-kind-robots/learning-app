## ADDED Requirements

### Requirement: Lesson exit replaces browser history
The system SHALL replace the current browser history entry when a learner exits a lesson through completion or cancellation.

#### Scenario: Finished lesson exit
- **WHEN** a learner completes the final lesson trial and exits the lesson flow
- **THEN** the rendered home screen replaces the current lesson history entry with `/home`
- **AND** the completed lesson screen is not restored by pressing browser Back

#### Scenario: Cancelled lesson exit
- **WHEN** a learner cancels an active lesson
- **THEN** the rendered home screen replaces the current lesson history entry with `/home`
- **AND** the cancelled lesson screen is not restored by pressing browser Back
