# lesson Specification

## Purpose
The lesson spec defines the behavior and state management for interactive learning sessions where users practice vocabulary through trials. It covers lesson initialization, trial progression, and configuration options for customizing the learning experience.
## Requirements
### Requirement: Lesson state includes options object
The system SHALL create lesson state with an options object containing configuration settings.

#### Scenario: Lesson initialization with options
- **WHEN** creating initial lesson state
- **THEN** the state includes an `options` object with `trial-selector`
- **AND** `trial-selector` defaults to `:random` if not specified

### Requirement: Lesson advancement uses options
The system SHALL use trial-selector from lesson options when advancing to next trials.

#### Scenario: Trial advancement with options
- **WHEN** advancing to the next trial
- **THEN** the selection uses the `trial-selector` from lesson `options`
- **AND** maintains backward compatibility for lessons without options

### Requirement: Lesson documents follow data-model spec
The system SHALL store lesson documents according to `specs/data-model/spec.md`.

#### Scenario: Lesson document shape
- **WHEN** a lesson is persisted
- **THEN** it matches the lesson document shape in `specs/data-model/spec.md`

### Requirement: Lesson trials follow data-model spec
The system SHALL store lesson trials according to `specs/data-model/spec.md`.

#### Scenario: Trial document shape
- **WHEN** a lesson trial is persisted
- **THEN** it matches the lesson trial shape in `specs/data-model/spec.md`

### Requirement: Lesson answer checks update lesson state
The system SHALL return updated lesson state after answer checks, recording the result in `:last-result`.

#### Scenario: Answer check response
- **WHEN** an answer is checked
- **THEN** the response includes `lesson-state` with `:last-result` containing `:correct?` and `:answer`

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word and each example, using denormalized prompts and answers.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each example produces an example trial
- **AND** example trials start in a locked state until the matching word trial is answered correctly

### Requirement: Lesson state is denormalized
The system SHALL store lesson state without a separate `:words` collection.

#### Scenario: Lesson state fields
- **WHEN** lesson state is stored
- **THEN** it includes `:trials`, `:remaining-trials`, `:current-trial`, and `:last-result`
- **AND** it omits a `:words` field

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

### Requirement: Lesson trial unlocking controls example visibility
The system SHALL keep example trials hidden from lesson selection until the matching word trial has been answered correctly.

#### Scenario: Lesson start selectable pool
- **WHEN** a lesson starts
- **THEN** the selectable trial pool contains only unlocked word trials
- **AND** locked example trials still count as lesson trials for progress tracking

#### Scenario: Successful word answer unlocks examples
- **WHEN** a user answers a word trial correctly
- **THEN** example trials with the same `word-id` become unlocked
- **AND** those unlocked example trials join the normal selectable pool
- **AND** the next selected trial is still chosen by the lesson trial-selector rather than forced to that example

### Requirement: Example answer rendering keeps structure annotations
The system SHALL keep example structure data on lesson example trials so the correct answer can be rendered with deterministic word annotations.

#### Scenario: Example trial stores answer structure
- **WHEN** an example trial is generated for a lesson
- **THEN** it keeps the example `structure` items alongside the denormalized prompt and answer

#### Scenario: Correct example answer is segmented by structure
- **WHEN** lesson UI renders the correct answer for an example trial
- **THEN** it builds answer segments from the German answer text plus each structure item `wordIndex`
- **AND** annotated segments keep the matching `dictionaryForm` and `translation`

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

