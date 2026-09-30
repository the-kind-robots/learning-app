## ADDED Requirements

### Requirement: A blank answer is not checked
Checking an answer that is empty or only whitespace SHALL do nothing: the trial stays
waiting for its answer, nothing is revealed, and no review is recorded.

#### Scenario: Checking with nothing typed
- **WHEN** the answer field is empty and the user activates «ПРОВЕРИТЬ»
- **THEN** the answer field is still on screen and no answer is revealed
- **AND** no review is recorded

### Requirement: A lesson in progress is not stored
The lesson in progress SHALL be held by the open app only and SHALL NOT be written to a database. What its answers record — the reviews — is written as before.

#### Scenario: Answering a lesson
- **WHEN** a learner starts a lesson and answers its trials
- **THEN** no lesson document is written to any local database
- **AND** a review is recorded for each checked word-trial answer

## MODIFIED Requirements

### Requirement: Lesson state is denormalized
The system SHALL hold lesson state without a separate `:words` collection.

#### Scenario: Lesson state fields
- **WHEN** a lesson starts
- **THEN** its state includes `:trials`, `:remaining-trials`, `:current-trial`, and `:last-result`
- **AND** it omits a `:words` field

### Requirement: Lesson entry starts a fresh session
The system SHALL start a new lesson session every time the user enters the lesson flow from the UI, drawn from the current vocabulary state.

#### Scenario: Re-entering lesson after leaving an unfinished session
- **WHEN** a user enters the lesson flow after leaving an unfinished session
- **THEN** the system creates a new lesson session from the current vocabulary state
- **AND** the newly rendered lesson does not continue the one that was left

### Requirement: Leaving a lesson returns home
The system SHALL show the home screen when a learner leaves a lesson — by finishing it, by closing it, or by the browser's or the system's Back — and leaving SHALL end the lesson, whichever way out was taken. Pressing the browser's Back on home SHALL NOT restore the lesson.

#### Scenario: Finished lesson exit
- **WHEN** a learner completes the final lesson trial and exits the lesson flow
- **THEN** the home screen is on display
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Cancelled lesson exit
- **WHEN** a learner closes an active lesson from the corner
- **THEN** the home screen is on display
- **AND** pressing the browser's Back does not show the lesson

#### Scenario: Leaving a lesson by Back
- **WHEN** a learner who has answered part of a lesson presses the browser's Back
- **THEN** the home screen is on display
- **AND** entering the lesson again starts a fresh session

### Requirement: Continuing a lesson advances one trial
Activating the continue button («ДАЛЕЕ» or «ЗАКОНЧИТЬ») once SHALL act once: one press of Enter on the focused button, or one click, advances the lesson by exactly one trial. A double click SHALL advance it by one trial as well.

#### Scenario: Enter on the continue button
- **WHEN** the continue button is focused after an answer and the user presses Enter once
- **THEN** the lesson shows the next trial, skipping none

#### Scenario: Double click on the continue button
- **WHEN** the user double-clicks the continue button
- **THEN** the lesson advances by one trial

## REMOVED Requirements

### Requirement: Lesson documents follow data-model spec
**Reason**: The lesson in progress is no longer stored; it is held by the open app only.
**Migration**: None. A lesson document left in device-db from an earlier version is never read.

### Requirement: Lesson trials follow data-model spec
**Reason**: Trials are part of the lesson state, which is no longer stored.
**Migration**: None.
