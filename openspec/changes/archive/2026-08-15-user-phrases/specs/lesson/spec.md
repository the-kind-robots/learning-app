# lesson Delta

## MODIFIED Requirements

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word, each phrase, and each example, using denormalized prompts and answers. Words and phrases SHALL be selected into the lesson from one shared pool ordered by retention. Phrases SHALL NOT produce example trials.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each example produces an example trial
- **AND** example trials start in a locked state until the matching word trial is answered correctly

#### Scenario: Phrase trial generation
- **WHEN** a lesson starts with a phrase among the selected items
- **THEN** the phrase produces a single phrase trial with the translation as prompt and the phrase text as answer
- **AND** no example trial is generated for the phrase

## ADDED Requirements

### Requirement: Phrase-trial answers record review progress
Graded phrase-trial answers SHALL write a review document on every graded attempt, as word trials do. A trial answered wrongly stays in the pool and may be graded again in the same lesson; each attempt is its own review.

#### Scenario: Both attempts are written
- **WHEN** a phrase trial is answered incorrectly and later correctly in the same lesson
- **THEN** a review with `retained` false and a review with `retained` true are written for that trial
