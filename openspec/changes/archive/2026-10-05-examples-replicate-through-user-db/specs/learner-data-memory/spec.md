## MODIFIED Requirements

### Requirement: The learner's data is held in memory as a projection of PouchDB
The client SHALL hold in app state what the screens show of the learner's data: every word and phrase with its search text normalised once, the reviews of each word, the collections, and the account's examples. Every one of them lives in user-db, so memory SHALL be a projection of user-db alone, and SHALL neither read nor follow device-db. PouchDB SHALL remain the only source of that data, and memory SHALL hold nothing PouchDB lacks, so that losing memory — a reload, a frozen tab — loses nothing: it is rebuilt from PouchDB.

Memory SHALL be loaded at start in one go, from a snapshot when one passes its checks (Requirement: A repeat start reads memory from a snapshot). Otherwise the client SHALL note where user-db's change feed stands, read every document of user-db, then read what it stored meanwhile, and only then put memory in place. From there it SHALL follow user-db's change feed and take every change in the order the database stored it — this app's own writes, another tab's and a synchronisation's alike. Because a live feed can drop a change without reporting it, memory SHALL catch up each time the page becomes visible again, before a decision that needs every collection, and before the example backfill counts what a replication pass brought: it SHALL read what the database stored after the last change it took. Memory SHALL record the feed position of the last change that changed it: the change's sequence and its document's id and revision. A change that the feed or a catch-up brings at or below that sequence SHALL change nothing, and a batch that changes nothing memory keeps, such as a pairing receipt, SHALL leave memory as it was, so that nothing renders for it. For each id, memory SHALL hold the last document the database stored, which is the winner replication picked. Memory SHALL be the same whatever order the load's documents arrive in and however they are grouped: a document whose revision memory already holds SHALL change nothing, a deleted document SHALL leave memory, a review MAY arrive before its word, and a document memory cannot take SHALL be left out and logged without stopping the rest, and the version of it that memory held SHALL leave memory, so that memory holds what a full read gives.

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

#### Scenario: A batch of tasks
- **WHEN** the change feed brings a batch of documents memory does not keep
- **THEN** memory stays the same value, and nothing renders for it

#### Scenario: The task queue writes
- **WHEN** the task queue writes to device-db
- **THEN** memory reads nothing for it and stays the same value

#### Scenario: The same revision twice
- **WHEN** the feed delivers a document at the revision memory already holds
- **THEN** memory is unchanged

#### Scenario: A deletion
- **WHEN** a word, review, collection or example is deleted in PouchDB
- **THEN** it is no longer in memory

#### Scenario: Any order, one memory
- **WHEN** the load's pages arrive in any order and the documents stored since the load began follow them in the order they were stored
- **THEN** memory equals the memory built from the final documents at once

### Requirement: Memory takes a write only after PouchDB accepted it
A write made by this app SHALL go to PouchDB first. A write PouchDB refuses SHALL leave memory untouched, so a screen never shows data that will not replicate. Once PouchDB has accepted it, the write SHALL catch memory up with user-db's change log, from memory's feed position, before the write completes, so that memory takes the write in the order the database stored it, as it takes every other change, and the screen that asked for it can show it at once. The write SHALL NOT apply the documents it wrote to memory by any other path, SHALL NOT wait for the live change feed, and SHALL NOT wait for device-db or fail because device-db cannot be read or written: device-db holds none of the learner's data.

A write SHALL read the document it changes from PouchDB by id — the winning revision — and SHALL put its change over that revision, whichever revision memory holds. When the put is refused as a conflict, the write SHALL read the document again and put once more. A change SHALL NOT overwrite what the stored version holds: adding a word that is already stored SHALL keep every translation of the stored word and add the new ones, and a change to a collection SHALL be made to the stored collection. Writes SHALL NOT wait for one another.

#### Scenario: Own write, then a screen
- **WHEN** the learner adds or edits a word and then opens a screen that shows it
- **THEN** the screen shows the word as written

#### Scenario: A refused write
- **WHEN** PouchDB refuses a write
- **THEN** memory holds what it held before the write

#### Scenario: A write while the feed brings nothing
- **WHEN** the change feed brings no change and the learner edits a word
- **THEN** the edit completes and memory holds the word as written

#### Scenario: A feed batch read before the write
- **WHEN** the change feed has read an earlier revision of a word and has not yet handed it to memory, and the learner edits that word
- **THEN** memory holds the edited word when the edit completes, and still holds it after the feed hands its batch over

#### Scenario: device-db cannot be read
- **WHEN** device-db can be neither read nor written and the learner adds a word
- **THEN** the add completes and memory holds the word

#### Scenario: A word with two branches
- **WHEN** replication leaves a word with two branches and the learner edits the word
- **THEN** the edit is put over the winning revision, and the other branch stays as replication left it

#### Scenario: A pull and an add of the same word
- **WHEN** a pull stores a word with one translation, and before memory holds it the learner adds the same word with another translation
- **THEN** the stored word has both translations

### Requirement: The app opens on a splash until memory is loaded
The app SHALL show a splash, and no screen, until memory holds everything user-db held at start; then the screen asked for SHALL be shown, with every word's retention. When the learner's data cannot be read, the splash SHALL say so, and the app SHALL keep trying to read it.

#### Scenario: Opening the app
- **WHEN** the app is opened
- **THEN** a splash is on display until memory is loaded
- **AND** the screen asked for is shown then, retention included

#### Scenario: The data cannot be read
- **WHEN** reading the learner's data fails at start
- **THEN** the splash says «Не получается прочитать данные на устройстве. Пробуем снова…»
- **AND** the screen asked for is shown once a read succeeds

### Requirement: A repeat start reads memory from a snapshot
The client SHALL keep a snapshot of memory in the Cache API, as one entry: what memory took from each document, a format version, a checksum of that content, the feed position of user-db that memory had taken changes up to — the sequence, and the id and revision of the change at it — and user-db's marker. user-db SHALL carry a marker, a random id kept in a local document of user-db and created when it is missing, so that a database that was re-created or cleared is told apart from the one the snapshot came from.

At start the client SHALL check the snapshot before this tab's synchronisation writes to user-db; screens, routing and the other start-up work SHALL NOT wait for the check. A write that lands before the check from anywhere else — a credential link, another tab — SHALL NOT make the check take a snapshot whose stored change user-db no longer holds. The client SHALL use the snapshot only when its format version is the current one, its checksum matches its content, user-db's marker equals the stored one, the stored sequence is at or below where user-db's feed stands, and user-db still holds the stored change: the same document at the same revision at that sequence, or, when a later change of that document replaced it there, a database that knows that revision. Memory SHALL then be the snapshot's content, taken again through the same path documents take, together with what user-db stored after the stored position; no document SHALL be read in full. When any check fails, or anything fails while the snapshot is read or taken, the client SHALL delete the snapshot and load memory by reading every document. A failed check on the marker, the sequence or the stored change SHALL delete the snapshot before this tab's synchronisation writes.

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
- **WHEN** user-db's marker differs from the one the snapshot stored
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
