## MODIFIED Requirements

### Requirement: Phrase translations are edited as a whole string
The edit dialog SHALL edit a phrase's translation as one multi-line string; saving SHALL NOT split it on punctuation. The German text of a word or a phrase SHALL be editable in the dialog as `specs/learner-data-memory/spec.md` states. The dialog's delete button SHALL read «Удалить слово».

#### Scenario: Editing keeps sentence punctuation
- **WHEN** the user saves the translation "Извини, что я опоздал."
- **THEN** the stored translation is that exact single entry
