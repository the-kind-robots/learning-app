## ADDED Requirements

### Requirement: A word keeps the part of speech of the suggestion it was picked from
A vocabulary document of a word added from a picked suggestion SHALL carry `pos`, the part of speech of that suggestion as the dictionary names it. The part of speech SHALL count only while the word field still holds the picked lemma; a word typed, or typed on after the pick, SHALL be stored without `pos`. A phrase SHALL NOT carry `pos`. `pos` is optional, so documents written before it need no migration, and a word added again whose document exists SHALL keep the stored document's `pos` as it is.

#### Scenario: A picked word keeps its part of speech
- **WHEN** a word is added after the adjective suggestion of "schön" was picked
- **THEN** its document carries the adjective's `pos`

#### Scenario: A typed word has none
- **WHEN** a word is added without a suggestion picked, or after the picked lemma was edited
- **THEN** its document has no `pos`
