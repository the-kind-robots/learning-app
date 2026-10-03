## MODIFIED Requirements

### Requirement: Adding a phrase creates a phrase document and queues an example fetch
Submitting the form in phrase mode SHALL create a vocabulary document of the `"phrase"` kind with the translation stored as a single entry (never split on punctuation), seed an initial review on the terms `specs/learner-data-memory/spec.md` sets for a word added again, add it to the active collection, and queue an example fetch carrying the active collection context. The fetch SHALL be queued on the same terms as for a word, including the re-fetch rule for an entry added to a named collection that has no example for it yet (see `specs/examples-schema/spec.md`). Re-adding an existing value SHALL merge translations as whole entries and add it to the active collection.

#### Scenario: Phrase with sentence translation survives punctuation
- **WHEN** the user adds "Entschuldigung, dass ich zu spät komme" with translation "Извини, что я опоздал."
- **THEN** one phrase document exists whose translation is the single entered string
- **AND** an example-fetch task is queued for that phrase, carrying the active collection context

#### Scenario: Duplicate phrase merges
- **WHEN** the user adds a phrase whose normalized value already exists
- **THEN** the translations are merged as whole entries and the document count does not grow

#### Scenario: Existing phrase added to a collection that has no example for it
- **WHEN** the user adds an existing phrase while a named collection is active and that collection has no example for it
- **THEN** an example-fetch task is queued for that phrase and collection, as it would be for a word
