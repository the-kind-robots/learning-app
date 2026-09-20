## MODIFIED Requirements

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word, each phrase, and each example, using denormalized prompts and answers. Words and phrases SHALL be selected into the lesson from one shared pool ordered by retention. A phrase SHALL produce example trials on the same terms as a word: every example stored for it becomes an example trial.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each example produces an example trial
- **AND** example trials start in a locked state until the vocabulary trial they belong to is answered correctly

#### Scenario: Phrase trial generation
- **WHEN** a lesson starts with a phrase among the selected items
- **THEN** the phrase produces a single phrase trial with the translation as prompt and the phrase text as answer
- **AND** each example stored for that phrase produces an example trial, locked until the phrase trial is answered correctly

### Requirement: Lesson trial unlocking controls example visibility
The system SHALL keep example trials hidden from lesson selection until the vocabulary trial carrying the same `word-id` has been answered correctly. That trial is a word trial for a word and a phrase trial for a phrase; the kind of the entry SHALL NOT change whether the lock applies.

#### Scenario: Lesson start selectable pool
- **WHEN** a lesson starts
- **THEN** the selectable trial pool contains only unlocked word and phrase trials
- **AND** locked example trials still count as lesson trials for progress tracking

#### Scenario: Successful word answer unlocks examples
- **WHEN** a user answers a word trial correctly
- **THEN** example trials with the same `word-id` become unlocked
- **AND** those unlocked example trials join the normal selectable pool
- **AND** the next selected trial is still chosen by the lesson trial-selector rather than forced to that example

#### Scenario: Successful phrase answer unlocks examples
- **WHEN** a user answers a phrase trial correctly
- **THEN** example trials with the same `word-id` become unlocked and join the normal selectable pool
- **AND** an incorrect phrase answer leaves them locked
