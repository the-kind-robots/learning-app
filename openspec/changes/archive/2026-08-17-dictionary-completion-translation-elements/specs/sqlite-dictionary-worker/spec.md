## MODIFIED Requirements

### Requirement: Worker completion result supports home autocomplete
The system SHALL return completion rows that the home add-word form can render and use to prefill translation. Each row's translations SHALL be carried as discrete elements from the query to the caller, so that no character occurring inside a translation can change how many translations a lemma has. The client SHALL NOT split a completion's translations on any separator.

#### Scenario: Non-empty completion result
- **WHEN** the input matches entries in the SQLite dictionary
- **THEN** the resolved value is a sequence of completion maps
- **AND** each result map contains lemma text, translations, and exact-match flag

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

### Requirement: Completion query cost scales with the answer, not the prefix range
The completions SQL SHALL select the ten winning lemmas before joining translations or computing the exact-match flag, so that per-row aggregation work is bounded by the result limit rather than by the number of surface forms matching the prefix. Carrying translations as discrete elements SHALL NOT reintroduce work proportional to the prefix range.

#### Scenario: Short prefix costs the same order as a long one
- **WHEN** completions run for a one-letter prefix matching tens of thousands of surface forms
- **THEN** translations are aggregated only for the ten returned lemmas
- **AND** the query does not build grouping or concatenation structures over the full prefix range

#### Scenario: Rewritten query returns identical rows
- **WHEN** the rewritten query runs for any prefix
- **THEN** its rows, columns, ordering, and limit match the previous grouping query exactly
- **AND** `has_exact` still reflects whether the lemma has a surface form equal to the normalized input

#### Scenario: The element-carrying aggregate is measured, not assumed
- **WHEN** the translation aggregate is changed
- **THEN** its cost is measured against the shipped dictionary on the prefixes `f`, `fe`, `fen`, `sch`, `hau` and `a`
- **AND** the before and after figures are recorded with the change
