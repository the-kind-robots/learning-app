## MODIFIED Requirements

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word, each phrase, and each example, using denormalized prompts and answers. A word or phrase SHALL produce at most one example trial: of the examples the lesson's collection sees for it, the one with the smallest id, so the same one every time. The other examples stay stored; a lesson does not show them until it can show several. A phrase SHALL produce example trials on the same terms as a word.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each word with examples produces one example trial
- **AND** example trials start in a locked state until the vocabulary trial they belong to is answered correctly

#### Scenario: A word with several examples
- **WHEN** a lesson starts with a word that has two examples the lesson's collection sees
- **THEN** the word produces one example trial, from the example with the smaller id

#### Scenario: Phrase trial generation
- **WHEN** a lesson starts with a phrase among the selected items
- **THEN** the phrase produces a single phrase trial with the translation as prompt and the phrase text as answer
- **AND** an example stored for that phrase produces one example trial, locked until the phrase trial is answered correctly
