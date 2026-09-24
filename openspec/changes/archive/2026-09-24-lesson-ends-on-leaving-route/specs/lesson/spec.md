## MODIFIED Requirements

### Requirement: Leaving a lesson returns home
The system SHALL show the home screen when a learner leaves a lesson — by finishing it, by closing it, or by the browser's or the system's Back — and leaving SHALL end the lesson: the stored lesson in progress is removed, whichever way out was taken. Pressing the browser's Back on home SHALL NOT restore the lesson.

#### Scenario: Finished lesson exit
- **WHEN** a learner completes the final lesson trial and exits the lesson flow
- **THEN** the home screen is on display and no lesson is stored
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Cancelled lesson exit
- **WHEN** a learner closes an active lesson from the corner
- **THEN** the home screen is on display and no lesson is stored
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Leaving a lesson by Back
- **WHEN** a learner who has answered part of a lesson presses the browser's Back
- **THEN** the home screen is on display and no lesson is stored
- **AND** entering the lesson again starts a fresh session
