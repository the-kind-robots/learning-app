## ADDED Requirements

### Requirement: An example remembers its language, and a read sees the learner's

An example document SHALL carry `language`, the language its translation is written in: the language the request for it asked (`specs/examples/spec.md`, Requirement: An example is generated in the learner's language), or the learner's language for an example the learner wrote. A document without `language`, stored before the field was, SHALL be Russian. The lesson, the word dialog and the backfill (`specs/example-backfill/spec.md`) SHALL see only the examples in the learner's language (`specs/interface-language/spec.md`), and every rule of this spec — the lookups by collection, the example a collection shows, replacing it — SHALL apply to those alone. Replacing the example of a pair SHALL leave its examples in other languages in place.

#### Scenario: The learner switches the language
- **WHEN** a word holds a Russian example and the learner's language becomes English
- **THEN** the word dialog shows no example for it, and the lesson asks no example trial of it
- **AND** the device asks for an English example of it

#### Scenario: The learner switches back
- **WHEN** the learner whose word holds a Russian and an English example switches from English back to Russian
- **THEN** the word dialog shows the Russian example

#### Scenario: An example stored before the field
- **WHEN** an example document has no `language` and the learner's language is Russian
- **THEN** the dialog shows it and the lesson asks it

#### Scenario: Replacing the example in English
- **WHEN** a pair holds a Russian and an English example and the English learner replaces its example
- **THEN** the pair holds the new English example and the Russian one
