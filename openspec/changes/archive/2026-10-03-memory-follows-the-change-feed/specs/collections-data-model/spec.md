## ADDED Requirements

### Requirement: Deleting a collection deletes the collection only
The system SHALL delete the collection document alone. The word documents SHALL remain: a word held only by the deleted collection stays in the vocabulary. The examples made for the collection SHALL remain stored; a collection made again has a new id, so they SHALL NOT show in it. They SHALL show in «Всё подряд», like every other example.

#### Scenario: Deleted collection leaves its words and examples
- **WHEN** a named collection holding two words is deleted
- **THEN** its document is gone
- **AND** both words and the examples made for it are still stored

#### Scenario: A collection made again
- **WHEN** a collection is deleted and a collection with the same name is made again
- **THEN** the examples made for the deleted one do not show in the new one

## REMOVED Requirements

### Requirement: Deleting a collection deletes its document and its examples
**Reason**: Examples are the learner's history and are never deleted with a collection; see "Deleting a collection deletes the collection only".
**Migration**: None. Examples deleted earlier stay deleted.
