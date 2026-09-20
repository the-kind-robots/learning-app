# collections-data-model Specification

## Purpose
Define how a collection is stored, where a word's membership of it lives, and how the active collection is remembered.
## Requirements
### Requirement: Collection documents are stored in the user database
The system SHALL store a collection as one document of `type: "collection"` in the user database (`:user/db`), carrying a `name`, an ISO 8601 creation timestamp and an array of word ids, under a generated document id. The repository SHALL hand it outward as `{:id :name :word-ids :created-at}`, with `word-ids` an empty vector when the document carries none.

#### Scenario: Collection document shape
- **WHEN** a collection is created
- **THEN** a document is stored in the user database with `type: "collection"`, the name, the creation timestamp and an empty word-ids array

Example — the storage layer writes the field names in snake case:
```json
{
  "_id": "6f0b1c8a-1f2d-4a1e-9d3c-0f5b7a2c9e41",
  "type": "collection",
  "name": "Driving School",
  "created_at": "2026-05-22T10:00:00.000Z",
  "word_ids": []
}
```

### Requirement: Membership is the collection's word-ids array
The system SHALL record that a word belongs to a collection by holding the word's id in that collection's `word-ids`, and SHALL store no separate membership document. Adding a word already listed SHALL write nothing. Deleting a word SHALL drop its id from every collection that holds it, in the same write that deletes the word.

#### Scenario: Adding a word under a named collection
- **WHEN** a word is added while a named collection is active
- **THEN** the word's id joins that collection's `word-ids` and no other document is written

#### Scenario: Adding a word under «Всё подряд»
- **WHEN** a word is added while no collection is active
- **THEN** no collection document is written

#### Scenario: Deleting a word
- **WHEN** a word held by two collections is deleted
- **THEN** both collection documents lose its id in the write that deletes the word

### Requirement: «Всё подряд» is the absence of an active collection
The system SHALL store no document for «Всё подряд»: it is the state in which nothing is remembered as the active collection. It SHALL NOT be deletable or renameable, and the themes screen SHALL list it first with the total words count.

#### Scenario: No document stands for «Всё подряд»
- **WHEN** the themes screen is opened with no collection documents stored
- **THEN** «Всё подряд» is the only tile and no collection document exists

#### Scenario: Words under «Всё подряд»
- **WHEN** no collection is active
- **THEN** the words list and a lesson draw from the whole vocabulary

### Requirement: The active collection persists across restarts
The system SHALL remember the id of the active collection across restarts, and SHALL treat the absence of a remembered id as «Всё подряд». Deleting the active collection SHALL clear what is remembered.

#### Scenario: Active collection restored on app load
- **WHEN** the app starts and a collection id is remembered
- **THEN** that collection is the active one

#### Scenario: Deleting the active collection
- **WHEN** the active collection is deleted
- **THEN** nothing is remembered as active and «Всё подряд» is the active one

### Requirement: Deleting a collection deletes its document and its examples
The system SHALL delete the collection document and purge the examples generated for that collection. The word documents SHALL remain: a word held only by the deleted collection stays in the vocabulary.

#### Scenario: Deleted collection leaves its words
- **WHEN** a named collection holding two words is deleted
- **THEN** its document is gone and its examples are purged
- **AND** both words are still in the vocabulary

### Requirement: A collection's scope is the union of its own words and its children's
The scope of a named collection — the words a lesson on it draws from and the words its vocabulary list shows — SHALL be the distinct union of its own `word-ids` and the `word-ids` of every collection whose folder key equals the collection's name. The folder key of a name is the text before its first `/`, trimmed; the comparison is trimmed and case-insensitive. Nesting is one level: `Kurs / A / B` is a child of `Kurs`, not of `Kurs / A`. A word added to a child SHALL appear in the parent's scope with no write to the parent document. Removing a word from the parent SHALL remove it from the parent document only.

#### Scenario: Lesson on a parent draws from the union
- **WHEN** `Kurs` is active, holds word a, and `Kurs / Kapitel 1` holds word b
- **THEN** a lesson on `Kurs` may draw both a and b

#### Scenario: A deeper slash stays with the first folder
- **WHEN** `Kurs`, `Kurs / A` and `Kurs / A / B` exist
- **THEN** `Kurs / A / B` is in the scope of `Kurs` and not in the scope of `Kurs / A`

#### Scenario: A word added to a child appears in the parent
- **WHEN** a word is added while `Kurs / Kapitel 1` is active
- **THEN** the vocabulary list on `Kurs` shows it, and the `Kurs` document is unchanged

#### Scenario: Removing from the parent leaves the child untouched
- **WHEN** word b is in `Kurs` and in `Kurs / Kapitel 1`, and the user removes b while `Kurs` is active
- **THEN** b leaves the `Kurs` document and stays in `Kurs / Kapitel 1`

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
