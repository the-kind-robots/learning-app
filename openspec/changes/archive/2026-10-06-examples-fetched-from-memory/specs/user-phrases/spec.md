## ADDED Requirements

### Requirement: Adding a phrase creates a phrase document, and its example is asked for like a word's
Submitting the form in phrase mode SHALL create a vocabulary document of the `"phrase"` kind with the translation stored as a single entry (never split on punctuation), seed an initial review on the terms `specs/learner-data-memory/spec.md` sets for a word added again, and add it to the active collection. Its example SHALL be asked for on the same terms as a word's (`specs/example-backfill/spec.md`), including the re-fetch rule for an entry added to a named collection that has no example for it yet (`specs/examples-schema/spec.md`). Re-adding an existing value SHALL merge translations as whole entries and add it to the active collection.

#### Scenario: Phrase with sentence translation survives punctuation
- **WHEN** the user adds "Entschuldigung, dass ich zu spät komme" with translation "Извини, что я опоздал."
- **THEN** one phrase document exists whose translation is the single entered string
- **AND** an example request is sent for that phrase in the active collection, carrying that translation

#### Scenario: Duplicate phrase merges
- **WHEN** the user adds a phrase whose normalized value already exists
- **THEN** the translations are merged as whole entries and the document count does not grow

#### Scenario: Existing phrase added to a collection that has no example for it
- **WHEN** the user adds an existing phrase while a named collection is active and that collection has no example for it
- **THEN** an example request is sent for that phrase and collection, as it would be for a word

## REMOVED Requirements

### Requirement: Adding a phrase creates a phrase document and queues an example fetch

**Reason**: Adding an entry queues nothing; its example is asked for from memory.
**Migration**: Requirement: Adding a phrase creates a phrase document, and its example is asked for
like a word's.
