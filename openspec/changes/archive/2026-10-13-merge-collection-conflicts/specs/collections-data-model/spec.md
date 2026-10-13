## ADDED Requirements

### Requirement: A conflicted collection merges its versions
A collection SHALL carry `renamed-at`, the time it was created or last renamed. When a collection has several live versions, the system SHALL write over the version with the latest `renamed-at` a collection whose word ids are the union of every live version's word ids and whose name is that version's name, and SHALL remove the other versions. A word removed on one device SHALL return when another device's version still lists it. A change written to a version that stopped being served SHALL be kept by the union.

#### Scenario: Two devices add different words offline
- **WHEN** two devices add different words to one collection before they sync
- **THEN** after both have synced the collection lists both words on both devices
- **AND** it has one live version

#### Scenario: Two devices rename offline
- **WHEN** two devices give one collection different names before they sync
- **THEN** the collection carries the name given last

#### Scenario: A rename and a later add on another device
- **WHEN** one device renames a collection and another device later adds a word to it before they sync
- **THEN** the collection keeps the rename and lists the added word

#### Scenario: A change on the version that lost
- **WHEN** a change was written to a version that another device's version then outranked
- **THEN** the merged collection lists what that change added

## MODIFIED Requirements

### Requirement: Deleting a collection deletes the collection only
The system SHALL delete the collection document alone, removing every live version it has. The word documents SHALL remain: a word held only by the deleted collection stays in the vocabulary. The examples made for the collection SHALL remain stored; a collection made again has a new id, so they SHALL NOT show in it. They SHALL show in «Всё подряд», like every other example. A collection deleted on one device and changed on another before they sync SHALL come back with the change: the live version wins, as PouchDB serves it.

#### Scenario: Deleted collection leaves its words and examples
- **WHEN** a named collection holding two words is deleted
- **THEN** its document is gone
- **AND** both words and the examples made for it are still stored

#### Scenario: A collection made again
- **WHEN** a collection is deleted and a collection with the same name is made again
- **THEN** the examples made for the deleted one do not show in the new one

#### Scenario: Deleting a conflicted collection
- **WHEN** a collection with two live versions is deleted
- **THEN** it does not come back from the other version

#### Scenario: Deleted on one device, edited on another
- **WHEN** one device deletes a collection that another device changed before they synced
- **THEN** after both have synced the collection is deleted on both
