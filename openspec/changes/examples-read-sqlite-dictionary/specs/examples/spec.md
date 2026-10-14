## MODIFIED Requirements

### Requirement: A generation request carries the word's part of speech and CEFR level

Before it builds a generation request the system SHALL look the target up in the dictionary, and
SHALL carry the found entry's part of speech and CEFR level in that request. A target the dictionary
does not know SHALL be sent without a part of speech and at the default CEFR level.

The dictionary is the SQLite dictionary built for the client; the system SHALL read it directly and
SHALL NOT depend on a separate dictionary database.

The lookup SHALL find a target of several words, spaces included, as it finds a single word.

The lookup SHALL answer from an index over the form it selects on, and SHALL NOT read the whole
dictionary to answer. The two outcomes the caller can observe — "the dictionary knows this word" and
"it does not" — SHALL NOT depend on how large the dictionary is.

A dictionary file that is missing or unreadable SHALL stop the system from starting, so a broken
dictionary is never mistaken for a word the dictionary does not know.

#### Scenario: A word the dictionary knows

- **WHEN** an example is generated for a word that has a dictionary entry with a part of speech and a
  CEFR level
- **THEN** the generation request carries that part of speech and that CEFR level

#### Scenario: A word the dictionary does not know

- **WHEN** an example is generated for a target with no dictionary entry
- **THEN** the generation request carries no part of speech and the default CEFR level
- **AND** generation proceeds

#### Scenario: A phrase without an article

- **WHEN** the dictionary is asked for a phrase of several words that has no article, such as "Bezug auf etwas nehmen"
- **THEN** the lookup answers without error, as for any word

#### Scenario: An article keeps the lemma distinct

- **WHEN** the dictionary is asked for "die Leiter"
- **THEN** only the entry written with that article is found, not "der Leiter"

#### Scenario: The lookup query is planned against an index

- **WHEN** the dictionary is asked for the entries of a normalized word
- **THEN** the query is answered from an index over the form, not by reading every row

#### Scenario: A dictionary loaded from scratch

- **WHEN** the system starts against a freshly built dictionary file
- **THEN** the lookup answers from it with no separate step, such as creating an index

#### Scenario: The dictionary file is missing

- **WHEN** the system starts and the dictionary file cannot be opened
- **THEN** it fails to start and says which file it could not open
