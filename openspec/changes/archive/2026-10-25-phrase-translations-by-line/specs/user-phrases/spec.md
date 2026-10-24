## RENAMED Requirements

- FROM: `### Requirement: Phrase translations are edited as a whole string`
- TO: `### Requirement: Phrase translations are edited one a line`

## MODIFIED Requirements

### Requirement: Phrase and translation inputs are multi-line
Both the German input and the translation input SHALL be auto-growing multi-line fields: single-row when compact, growing with content up to a maximum height, then scrolling internally. Auto-growing SHALL work on Safari (JS fallback when `field-sizing: content` is unsupported). In the German text of a phrase, line breaks SHALL be visual only and SHALL be collapsed to single spaces on submit; in the translation of a word or a phrase each line SHALL be a translation of its own (`specs/data-model/spec.md`).

#### Scenario: Long phrase grows the field
- **WHEN** the user pastes a phrase longer than one visual line
- **THEN** the field grows to show the content up to the maximum height without horizontal scrolling

### Requirement: Phrase translations are edited one a line
The edit dialog SHALL edit a phrase's translations as one multi-line text, one translation to a line; saving SHALL NOT split a line on punctuation. The German text of a word or a phrase SHALL be editable in the dialog as `specs/learner-data-memory/spec.md` states. The dialog's delete button SHALL read «Удалить слово».

#### Scenario: Editing keeps sentence punctuation
- **WHEN** the user saves the translation "Извини, что я опоздал."
- **THEN** the stored translation is that exact single entry

#### Scenario: Each line is a translation
- **WHEN** the user saves a phrase's translations "в любом случае" and "обязательно" on two lines
- **THEN** the phrase holds those two translation entries

### Requirement: Adding a phrase creates a phrase document, and its example is asked for like a word's
Submitting the form in phrase mode SHALL create a vocabulary document of the `"phrase"` kind with each line of the translation stored as an entry of its own (a line never split on punctuation), seed an initial review on the terms `specs/learner-data-memory/spec.md` sets for a word added again, and add it to the active collection. Its example SHALL be asked for on the same terms as a word's (`specs/example-backfill/spec.md`), including the re-fetch rule for an entry added to a named collection that has no example for it yet (`specs/examples-schema/spec.md`). Re-adding an existing value SHALL merge translations as whole entries and add it to the active collection.

#### Scenario: Phrase with sentence translation survives punctuation
- **WHEN** the user adds "Entschuldigung, dass ich zu spät komme" with translation "Извини, что я опоздал."
- **THEN** one phrase document exists whose translation is the single entered string
- **AND** an example request is sent for that phrase in the active collection, carrying that translation

#### Scenario: Phrase with translations on two lines
- **WHEN** the user adds "auf jeden Fall" with "в любом случае" and "обязательно" on two lines of the translation
- **THEN** one phrase document exists with those two translation entries

#### Scenario: Duplicate phrase merges
- **WHEN** the user adds a phrase whose normalized value already exists
- **THEN** the translations are merged as whole entries and the document count does not grow

#### Scenario: Existing phrase added to a collection that has no example for it
- **WHEN** the user adds an existing phrase while a named collection is active and that collection has no example for it
- **THEN** an example request is sent for that phrase and collection, as it would be for a word
