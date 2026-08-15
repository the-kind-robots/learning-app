# data-model Delta

## ADDED Requirements

### Requirement: A vocabulary document carries its kind
The system SHALL store phrases as vocabulary documents: `type` `"vocab"`, the content-addressed `_id` of ADR-0008 (`vocab:` plus the normalized value, spaces preserved), and `kind` `"phrase"`. A document without `kind` SHALL be read as a word, so documents written before phrases existed need no migration. A phrase's `value` SHALL have its whitespace collapsed and its `translation` entries SHALL be whole strings, never split on punctuation. Review documents SHALL reference phrases through the existing `word-id` field.

#### Scenario: Phrase document shape
- **WHEN** a phrase "auf jeden Fall" with translation "во всяком случае" is stored
- **THEN** the document `_id` is `vocab:auf jeden fall`
- **AND** it includes `type` `"vocab"`, `kind` `"phrase"`, `value`, a single-entry `translation`, `created-at`, and `modified-at`

#### Scenario: A document without a kind is a word
- **WHEN** a vocabulary document has no `kind` field
- **THEN** it is treated as a word everywhere the kind is read

#### Scenario: One value is one document
- **WHEN** a phrase is added whose value already exists as a word
- **THEN** the translations merge into that document and its `kind` is left unchanged

#### Scenario: Phrase reviews reference the phrase id
- **WHEN** a phrase trial is graded
- **THEN** the review document's `word-id` is the phrase document's `_id`
