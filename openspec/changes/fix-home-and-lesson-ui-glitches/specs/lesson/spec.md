## MODIFIED Requirements

### Requirement: Lesson answer checks update lesson state
The system SHALL return updated lesson state after answer checks without causing unstable prompt-card movement in the lesson UI.

#### Scenario: Answer check response keeps the task area visually stable
- **WHEN** a user checks an answer on the lesson screen
- **THEN** the response includes `lesson-state` with `:last-result` containing `:correct?` and `:answer`
- **AND** the lesson prompt/task area remains visually stable instead of jumping when result UI appears
