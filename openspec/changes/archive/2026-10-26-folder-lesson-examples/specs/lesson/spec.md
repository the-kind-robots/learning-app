## MODIFIED Requirements

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word, each phrase, and each example, using denormalized prompts and answers. A word or phrase SHALL produce at most one example trial: of the examples the lesson's collection sees for it, the one with the smallest id, so the same one every time. The other examples stay stored; a lesson does not show them until it can show several. A phrase SHALL produce example trials on the same terms as a word. A lesson on a parent collection SHALL see, for each word, the examples made for the parent when there are any, otherwise those made for its children.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each word with examples produces one example trial
- **AND** example trials start in a locked state until the vocabulary trial they belong to is answered correctly

#### Scenario: A word with several examples
- **WHEN** a lesson starts with a word that has two examples the lesson's collection sees
- **THEN** the word produces one example trial, from the example with the smaller id

#### Scenario: A word that lies only in a child
- **WHEN** a lesson starts on the parent `Reisen` with a word that lies only in its child `Reisen/Bahn`, which has an example of it
- **THEN** the word produces one example trial, from the child's example

#### Scenario: A word with an example in the parent and in a child
- **WHEN** a lesson starts on a parent with a word that has an example made for the parent and one made for a child
- **THEN** the example trial is the parent's

#### Scenario: Phrase trial generation
- **WHEN** a lesson starts with a phrase among the selected items
- **THEN** the phrase produces a single phrase trial with the translation as prompt and the phrase text as answer
- **AND** an example stored for that phrase produces one example trial, locked until the phrase trial is answered correctly
