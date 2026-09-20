## MODIFIED Requirements

### Requirement: Collection names are compared trimmed and case-insensitively
A collection's name SHALL be unique after trimming, case-insensitively. Creating a collection SHALL refuse a name that equals an existing collection's name by that comparison, renaming a collection SHALL refuse a name that equals another collection's name by it — the current name stays and nothing is written — and the parent of a folder SHALL be found by the same comparison. A collection's own name in another case is a rename, not a duplicate.

#### Scenario: Duplicate by case
- **WHEN** a collection `Kurs` exists and the user creates `kurs`
- **THEN** no collection is created

#### Scenario: Rename to a taken name
- **WHEN** collections `Kurs` and `Kurs / Kapitel 1` exist and the user renames `Kurs` to ` kurs / kapitel 1 `
- **THEN** `Kurs` keeps its name and no document is written

#### Scenario: Rename to the own name in another case
- **WHEN** a collection `Kurs` exists and the user renames it to `KURS`
- **THEN** the document is renamed to `KURS`
