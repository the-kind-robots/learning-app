## MODIFIED Requirements

### Requirement: A repeat start reads memory from a snapshot
The client SHALL keep a snapshot of memory in the Cache API, as one entry: memory itself — each word with its search text and its review history, the collections and the examples — a format version, a checksum of that content, the feed position of user-db that memory had taken changes up to — the sequence, and the id and revision of the change at it — and user-db's id. user-db SHALL carry an id, a random string kept in a local document of user-db and created when it is missing, so that a database that was re-created or cleared is told apart from the one the snapshot came from.

At start the client SHALL check the snapshot before this tab's synchronisation writes to user-db; screens, routing and the other start-up work SHALL NOT wait for the check. A write that lands before the check from anywhere else — a credential link, another tab — SHALL NOT make the check take a snapshot whose stored change user-db no longer holds. The client SHALL use the snapshot only when its format version is the current one, its checksum matches its content, user-db's id equals the stored one, the stored sequence is at or below where user-db's feed stands, and user-db still holds the stored change: the same document at the same revision at that sequence, or, when a later change of that document replaced it there, a database that knows that revision. Memory SHALL then be the snapshot's content, together with what user-db stored after the stored position, and SHALL read as the memory a full read gives; no document SHALL be read in full. When any check fails, or anything fails while the snapshot is read or taken, the client SHALL delete the snapshot and load memory by reading every document. A failed check on the id, the sequence or the stored change SHALL delete the snapshot before this tab's synchronisation writes.

The snapshot SHALL be a cache only: writes SHALL keep going to PouchDB alone, and the snapshot SHALL NOT be migrated — a change to the shape of memory or to what memory takes from a document, including a document an earlier build could not take, SHALL change the format version. The client SHALL write the snapshot once memory is loaded and each time the page goes to the background, and SHALL NOT write it when memory's feed position equals that of the snapshot last written or read. The memory written and its feed position SHALL be taken from one memory value. A write that does not finish SHALL leave the previous snapshot in place. A new build of the service worker SHALL keep the snapshot.

#### Scenario: A repeat start
- **WHEN** the app starts with a snapshot that passes its checks
- **THEN** memory equals the memory a full read gives
- **AND** no document of user-db is read in full

#### Scenario: A repeat start reads as a full read
- **WHEN** the app starts with a snapshot that passes its checks
- **THEN** every word has the retention level it has in the memory a full read gives
- **AND** a lesson picks from the same words, in the same order of urgency, and the lists and collections show the same

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

### Requirement: The learner's data is held in memory as a projection of user-db
The client SHALL hold in app state what the screens show of the learner's data: every word and phrase with its search text normalised once, the reviews of each word, the collections, and the account's examples. Every one of them lives in user-db, so memory SHALL be a projection of user-db alone, and SHALL neither read nor follow device-db. PouchDB SHALL remain the only source of that data, and memory SHALL hold nothing PouchDB lacks, so that losing memory — a reload, a frozen tab — loses nothing: it is rebuilt from PouchDB.

Memory SHALL be loaded at start in one go, from a snapshot when one passes its checks (Requirement: A repeat start reads memory from a snapshot). Otherwise the client SHALL note where user-db's change feed stands, read every document of user-db, then read what it stored meanwhile, and only then put memory in place. From there it SHALL follow user-db's change feed and take every change in the order the database stored it — this app's own writes, another tab's and a synchronisation's alike. Because a live feed can drop a change without reporting it, memory SHALL catch up each time the page becomes visible again, and before a decision that needs every collection: it SHALL read what the database stored after the last change it took. Memory SHALL record the feed position of the last change that changed it: the change's sequence and its document's id and revision. A change that the feed or a catch-up brings at or below that sequence SHALL change nothing, and a batch that changes nothing memory keeps, such as a pairing receipt, SHALL leave memory as it was, so that nothing renders for it. For each id, memory SHALL hold the last document the database stored, which is the winner replication picked. Memory SHALL be the same whatever order the load's documents arrive in and however they are grouped: a document whose revision memory already holds SHALL leave memory holding what it held, a deleted document SHALL leave memory, except a review, which stays once taken, a review MAY arrive before its word, and a document memory cannot take SHALL be left out and logged without stopping the rest, and the version of it that memory held SHALL leave memory, so that memory holds what a full read gives.

#### Scenario: Loaded at start
- **WHEN** the app starts on a device holding words, reviews, collections and examples
- **THEN** memory holds every one of them once the load completes

#### Scenario: A write during the load
- **WHEN** a document is written after the load began and before it completes
- **THEN** memory holds that document once the load completes

#### Scenario: A change the feed dropped
- **WHEN** a document is stored and the change feed does not bring it
- **THEN** memory holds it once the page becomes visible again

#### Scenario: A catch-up that resolves after the feed
- **WHEN** a catch-up reads a document, the feed then brings a newer revision of it, and the catch-up's answer arrives after that
- **THEN** memory holds the newer revision

#### Scenario: An edit that wins over a deletion
- **WHEN** a word deleted on one device at a higher revision is edited on another device at a lower one, and replication makes the edit the winner
- **THEN** memory holds the edited word

#### Scenario: A revision memory cannot take
- **WHEN** memory holds a word and a new revision of it arrives that memory cannot take
- **THEN** memory no longer holds the word, as a full read would not

#### Scenario: A batch memory does not keep
- **WHEN** the change feed brings a batch of documents memory does not keep
- **THEN** memory stays the same value, and nothing renders for it

#### Scenario: device-db is written
- **WHEN** the start deletes what an earlier build left in device-db
- **THEN** memory reads nothing for it and stays the same value

#### Scenario: The same revision twice
- **WHEN** the feed delivers a document at the revision memory already holds
- **THEN** memory holds what it held

#### Scenario: A deletion
- **WHEN** a word, collection or example is deleted in PouchDB
- **THEN** it is no longer in memory

#### Scenario: Any order, one memory
- **WHEN** the load's pages arrive in any order and the documents stored since the load began follow them in the order they were stored
- **THEN** memory equals the memory built from the final documents at once
