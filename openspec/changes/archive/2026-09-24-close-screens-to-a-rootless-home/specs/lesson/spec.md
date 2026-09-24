## REMOVED Requirements

### Requirement: Lesson exit replaces browser history
**Reason**: The lesson no longer manages its history entry itself; every way home follows the app's navigation rule, under which a finished or cancelled lesson is not restored by Back either.
**Migration**: See `app-navigation`, «Home has no in-app screen behind it».

## ADDED Requirements

### Requirement: Leaving a lesson returns home
The system SHALL show the home screen when a learner finishes a lesson or cancels it, and pressing the browser's Back there SHALL NOT restore the lesson.

#### Scenario: Finished lesson exit
- **WHEN** a learner completes the final lesson trial and exits the lesson flow
- **THEN** the home screen is on display
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Cancelled lesson exit
- **WHEN** a learner cancels an active lesson
- **THEN** the home screen is on display
- **AND** pressing the browser's Back does not show the lesson
