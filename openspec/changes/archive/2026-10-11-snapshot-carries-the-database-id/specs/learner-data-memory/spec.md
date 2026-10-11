## MODIFIED Requirements

### Requirement: A repeat start reads memory from a snapshot
The client SHALL keep a snapshot of memory in the Cache API, as one entry: what memory took from each document, a format version, a checksum of that content, the feed position of user-db that memory had taken changes up to — the sequence, and the id and revision of the change at it — and user-db's id. user-db SHALL carry an id, a random string kept in a local document of user-db and created when it is missing, so that a database that was re-created or cleared is told apart from the one the snapshot came from.

At start the client SHALL check the snapshot before this tab's synchronisation writes to user-db; screens, routing and the other start-up work SHALL NOT wait for the check. A write that lands before the check from anywhere else — a credential link, another tab — SHALL NOT make the check take a snapshot whose stored change user-db no longer holds. The client SHALL use the snapshot only when its format version is the current one, its checksum matches its content, user-db's id equals the stored one, the stored sequence is at or below where user-db's feed stands, and user-db still holds the stored change: the same document at the same revision at that sequence, or, when a later change of that document replaced it there, a database that knows that revision. Memory SHALL then be the snapshot's content, taken again through the same path documents take, together with what user-db stored after the stored position; no document SHALL be read in full. When any check fails, or anything fails while the snapshot is read or taken, the client SHALL delete the snapshot and load memory by reading every document. A failed check on the id, the sequence or the stored change SHALL delete the snapshot before this tab's synchronisation writes.

The snapshot SHALL be a cache only: writes SHALL keep going to PouchDB alone, and the snapshot SHALL NOT be migrated — a change to what memory takes from a document, including a document an earlier build could not take, SHALL change the format version. The client SHALL write the snapshot once memory is loaded and each time the page goes to the background, and SHALL NOT write it when memory's feed position equals that of the snapshot last written or read. The memory written and its feed position SHALL be taken from one memory value. A write that does not finish SHALL leave the previous snapshot in place. A new build of the service worker SHALL keep the snapshot.

#### Scenario: A repeat start
- **WHEN** the app starts with a snapshot that passes its checks
- **THEN** memory equals the memory a full read gives
- **AND** no document of user-db is read in full

#### Scenario: Stored since the snapshot
- **WHEN** words are added, edited and deleted after the snapshot was written, and the app starts again
- **THEN** memory holds each change

#### Scenario: The first start
- **WHEN** the app starts with no snapshot
- **THEN** memory is loaded by reading every document
- **AND** a snapshot is written once memory is loaded

#### Scenario: Another format version
- **WHEN** the snapshot carries a format version other than the current one
- **THEN** it is deleted and memory is loaded by reading every document

#### Scenario: A damaged snapshot
- **WHEN** the snapshot's content does not match its checksum
- **THEN** it is deleted and memory is loaded by reading every document

#### Scenario: A re-created database
- **WHEN** user-db's id differs from the one the snapshot stored
- **THEN** the snapshot is deleted before synchronisation starts, and memory is loaded by reading every document

#### Scenario: A database that lost its last writes and stored others
- **WHEN** user-db lost the change at the stored position, and another change now sits at that sequence
- **THEN** the snapshot is deleted before synchronisation starts, and memory is loaded by reading every document

#### Scenario: The lost change brought back
- **WHEN** user-db lost the change at the stored position, and before the start a replication from elsewhere brought the same revision back under that sequence
- **THEN** the snapshot is taken, and memory equals the memory a full read gives

#### Scenario: A database behind the snapshot
- **WHEN** the stored feed position is ahead of where user-db's feed stands, as when the database lost its last writes
- **THEN** the snapshot is deleted before synchronisation starts, and memory is loaded by reading every document

#### Scenario: Going to the background
- **WHEN** memory has taken changes since the snapshot was written and the page goes to the background
- **THEN** a snapshot of the memory at that moment, with its feed position, replaces the previous one

#### Scenario: A new build
- **WHEN** a new build of the service worker activates
- **THEN** the snapshot is still there
