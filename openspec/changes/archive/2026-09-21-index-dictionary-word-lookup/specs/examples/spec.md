## ADDED Requirements

### Requirement: A generation request carries the word's part of speech and CEFR level

Before it builds a generation request the system SHALL look the target up in the dictionary, and
SHALL carry the found entry's part of speech and CEFR level in that request. A target the dictionary
does not know SHALL be sent without a part of speech and at the default CEFR level.

The lookup SHALL answer from an index over the field it selects on, and SHALL NOT read the whole
dictionary to answer. The two outcomes the caller can observe — "the dictionary knows this word" and
"it does not" — SHALL NOT depend on how large the dictionary is: a lookup that degrades into a full
read exceeds its HTTP timeout, and a timed-out lookup is indistinguishable from an unknown word, so
every request would silently lose both fields.

The index SHALL be created by the system itself rather than by hand, so a dictionary loaded from
scratch is queryable without a separate step.

#### Scenario: A word the dictionary knows

- **WHEN** an example is generated for a word that has a dictionary entry with a part of speech and a
  CEFR level
- **THEN** the generation request carries that part of speech and that CEFR level

#### Scenario: A word the dictionary does not know

- **WHEN** an example is generated for a target with no dictionary entry
- **THEN** the generation request carries no part of speech and the default CEFR level
- **AND** generation proceeds

#### Scenario: The lookup query is planned against an index

- **WHEN** the dictionary is asked for the entries of a normalized word
- **THEN** the query is answered from the index over that field, not by reading every document
- **AND** it completes well inside the lookup's HTTP timeout

#### Scenario: A dictionary loaded from scratch

- **WHEN** the system starts against a dictionary database that has no such index
- **THEN** it creates the index
- **AND** a database that already has it is left unchanged
- **AND** a dictionary database that cannot be reached SHALL NOT stop the system from starting
