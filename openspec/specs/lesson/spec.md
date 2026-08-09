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

### Requirement: Error reveal labels the correct answer with short copy
The lesson error footer SHALL label the revealed correct answer with the header «Правильно:».

#### Scenario: Wrong answer reveal
- **WHEN** a user answers a trial incorrectly
- **THEN** the footer shows the user's answer under «Ваш ответ:»
- **AND** the correct answer under «Правильно:»

### Requirement: Answer field suppresses text assistance and wraps long input
The lesson answer field SHALL be a multi-line textarea with browser text assistance disabled — `spellcheck="false"`, `autocorrect="off"`, `autocapitalize="none"`, `autocomplete="off"` — so German input is not corrected against the user, and long answers SHALL wrap onto new lines inside the field.

#### Scenario: Text assistance attributes present
- **WHEN** the lesson input footer renders
- **THEN** the answer field is a textarea with spellcheck, autocorrect, autocapitalize and autocomplete disabled

#### Scenario: Long answer wraps
- **WHEN** the typed answer exceeds the field width
- **THEN** the text soft-wraps onto further lines within the textarea

### Requirement: Answer token click opens token-info card

The lesson UI SHALL open a token-info card when the user activates an annotated word in the revealed correct answer. The card SHALL render as a popover anchored to the activated word — not as a centered modal — with an arrow pointing at the word, and SHALL reflect the word's vocabulary state, offering the matching action.

#### Scenario: Unknown word

- **WHEN** the user clicks an annotated word whose dictionary form is not in the vocabulary
- **THEN** a popover opens anchored above the word (below when there is no room above), showing the dictionary form and translation
- **AND** the popover offers an add-to-dictionary action that saves the word with the translation

#### Scenario: Known word missing this translation

- **WHEN** the user clicks an annotated word that exists in the vocabulary without this translation
- **THEN** the popover offers an add-translation action that merges the translation into the word

#### Scenario: Known word with translation

- **WHEN** the user clicks an annotated word already stored with this translation
- **THEN** the popover shows the word as already in the dictionary and dismisses itself after a short delay

#### Scenario: Dismissal

- **WHEN** the popover is open
- **THEN** clicking outside it or pressing Escape closes it
- **AND** on hover-capable devices it auto-closes after an idle timeout unless the pointer or focus is inside it
- **AND** no modal chrome, backdrop dimming, or forced focus ring is presented

### Requirement: Revealed answer is selectable as continuous text

The revealed correct answer SHALL be selectable with the mouse as one continuous text run, across plain and hinted words alike, so it can be copied whole. Hinted words SHALL remain activatable.

#### Scenario: Selecting across hinted words

- **WHEN** the user drags a selection across the revealed answer
- **THEN** the selection includes the hinted words' text
- **AND** the copied text matches the visible sentence

#### Scenario: Hinted word still opens the hint

- **WHEN** the user clicks a hinted word without dragging
- **THEN** the hint card opens as before

