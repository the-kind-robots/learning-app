# data-model Delta

## ADDED Requirements

### Requirement: Phrase documents are stored
The system SHALL store phrase documents in the replicated user database with `type` `"phrase"`, a content-addressed `_id` of `phrase:` plus the normalized value (spaces preserved), the entered `value` with collapsed whitespace, a `translation` array whose entries are whole strings never split on punctuation, and ISO 8601 `created-at`/`modified-at` timestamps. Review documents SHALL reference phrases through the existing `word-id` field.

#### Scenario: Phrase document shape
- **WHEN** a phrase "auf jeden Fall" with translation "во всяком случае" is stored
- **THEN** the document `_id` is `phrase:auf jeden fall`
- **AND** it includes `type` `"phrase"`, `value`, a single-entry `translation`, `created-at`, and `modified-at`

#### Scenario: Phrase reviews reference the phrase id
- **WHEN** a phrase trial is graded
- **THEN** the review document's `word-id` is the phrase document's `_id`
