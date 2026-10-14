## ADDED Requirements

### Requirement: A collection's scope is the union of its own words and its children's, and removal covers both
The scope of a named collection — the words a lesson on it draws from and the words its vocabulary list shows — SHALL be the distinct union of its own `word-ids` and the `word-ids` of every collection whose folder key equals the collection's name. The folder key of a name is the text before its first `/`, trimmed; the comparison is trimmed and case-insensitive. Nesting is one level: `Kurs / A / B` is a child of `Kurs`, not of `Kurs / A`. A word added to a child SHALL appear in the parent's scope with no write to the parent document. Removing a word while the parent is active SHALL remove it from the parent document and from every child that lists it, so that it leaves the list the user is looking at.

#### Scenario: Lesson on a parent draws from the union
- **WHEN** `Kurs` is active, holds word a, and `Kurs / Kapitel 1` holds word b
- **THEN** a lesson on `Kurs` may draw both a and b

#### Scenario: A deeper slash stays with the first folder
- **WHEN** `Kurs`, `Kurs / A` and `Kurs / A / B` exist
- **THEN** `Kurs / A / B` is in the scope of `Kurs` and not in the scope of `Kurs / A`

#### Scenario: A word added to a child appears in the parent
- **WHEN** a word is added while `Kurs / Kapitel 1` is active
- **THEN** the vocabulary list on `Kurs` shows it, and the `Kurs` document is unchanged

#### Scenario: Removing from the parent removes it from the children
- **WHEN** word b is in `Kurs` and in `Kurs / Kapitel 1`, and the user removes b while `Kurs` is active
- **THEN** b leaves both documents, the vocabulary list on `Kurs` no longer shows it, and «Всё подряд» still does

#### Scenario: A word held only by a child is removed
- **WHEN** word b is only in `Kurs / Kapitel 1`, and the user removes b while `Kurs` is active
- **THEN** b leaves `Kurs / Kapitel 1` and the vocabulary list on `Kurs` no longer shows it

## REMOVED Requirements

### Requirement: A collection's scope is the union of its own words and its children's
**Reason**: Its removal sentence left a word held by a child on the list after the user removed it from the parent (#562). The requirement is restated under a new name with the removal reaching the children.
**Migration**: None. The restated requirement keeps the scope rule and its scenarios.
