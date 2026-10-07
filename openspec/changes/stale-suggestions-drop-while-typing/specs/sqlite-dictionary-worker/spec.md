## MODIFIED Requirements

### Requirement: Worker completion result supports home autocomplete
The system SHALL return completion rows that the home add-word form can render and use to prefill translation. Each row's translations SHALL be carried as discrete elements from the query to the caller, so that no character occurring inside a translation can change how many translations a lemma has. The client SHALL NOT split a completion's translations on any separator. Each row SHALL also carry, as discrete normalised elements, the forms of its lemma that matched the prefix, gathered in the range scan the query already makes and not by a further scan per row.

#### Scenario: Non-empty completion result
- **WHEN** the input matches entries in the SQLite dictionary
- **THEN** the resolved value is a sequence of completion maps
- **AND** each result map contains lemma text, translations, exact-match flag, and the matched forms

#### Scenario: Matched forms name what the prefix found
- **WHEN** the prefix is `ging` and `gehen` is among the ten
- **THEN** its matched forms are the forms of `gehen` that start with `ging`, normalised, and no other

#### Scenario: No-match completion result
- **WHEN** the input matches no entries
- **THEN** the resolved value is empty

#### Scenario: A translation containing punctuation stays one translation
- **WHEN** a matching lemma has a translation whose text contains a comma, semicolon or full stop
- **THEN** that translation appears in the result as a single element, character for character as stored
- **AND** the lemma's translation count equals the number of rows stored for it in the dictionary

#### Scenario: Translation order follows dictionary rank
- **WHEN** a matching lemma has several translations
- **THEN** they appear in ascending `rank` order

#### Scenario: Lemma with no translations
- **WHEN** a matching lemma has no rows in the translations table
- **THEN** its translations are an empty sequence
- **AND** no blank translation is produced
