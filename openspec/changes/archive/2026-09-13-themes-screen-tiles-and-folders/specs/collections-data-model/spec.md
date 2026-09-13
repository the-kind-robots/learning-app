## ADDED Requirements

### Requirement: A collection's scope is the union of its own words and its children's
The scope of a named collection — the words a lesson on it draws from and the words its vocabulary list shows — SHALL be the distinct union of its own `word-ids` and the `word-ids` of every collection whose name, trimmed, starts case-insensitively with the collection's trimmed name followed by `/`. A word added to a child SHALL appear in the parent's scope with no write to the parent document. Removing a word from the parent SHALL remove it from the parent document only.

#### Scenario: Lesson on a parent draws from the union
- **WHEN** `Kurs` is active, holds word a, and `Kurs / Kapitel 1` holds word b
- **THEN** a lesson on `Kurs` may draw both a and b

#### Scenario: A word added to a child appears in the parent
- **WHEN** a word is added while `Kurs / Kapitel 1` is active
- **THEN** the vocabulary list on `Kurs` shows it, and the `Kurs` document is unchanged

#### Scenario: Removing from the parent leaves the child untouched
- **WHEN** word b is in `Kurs` and in `Kurs / Kapitel 1`, and the user removes b while `Kurs` is active
- **THEN** b leaves the `Kurs` document and stays in `Kurs / Kapitel 1`

### Requirement: Collection names are compared trimmed and case-insensitively
Creating a collection SHALL refuse a name that equals an existing collection's name after trimming, case-insensitively, and the parent of a folder SHALL be found by the same comparison.

#### Scenario: Duplicate by case
- **WHEN** a collection `Kurs` exists and the user creates `kurs`
- **THEN** no collection is created
