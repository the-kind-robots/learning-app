# learner-data-memory Specification

## Purpose
Define how the learner's data is held in memory as a projection of PouchDB, and how screens answer from it within one frame of the tap.

## Requirements

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

### Requirement: A screen shows the learner's data as it was when it was opened
A screen SHALL compute what it shows from memory when it is opened, and SHALL NOT change on its own while it is open. A document written elsewhere — another tab of the app, a synchronisation pull from another device — SHALL reach memory through the change feed and SHALL show on the next screen opened. A change the learner makes on the open screen — a word edited or removed, a collection created, renamed or deleted, a word added on home — SHALL show on that screen at once.

#### Scenario: Another tab adds a word
- **WHEN** the words screen is open in one tab and a word is added in another tab of the app
- **THEN** the open words screen does not change
- **AND** the word is listed once the words screen is opened again

#### Scenario: A pull brings a word from another device
- **WHEN** the words screen is open and a synchronisation pull brings a word added on another device
- **THEN** the open words screen does not change
- **AND** the word is listed once the words screen is opened again

#### Scenario: The learner's own edit
- **WHEN** the learner saves a change to a word on the open words screen
- **THEN** the list shows the change at once

### Requirement: Screens answer from memory, not from storage
Opening home, words, lesson or the themes screen, and typing in the words filter, SHALL read no document from PouchDB; each SHALL compute what it shows from memory.

#### Scenario: Opening a screen
- **WHEN** the learner opens home, words, lesson or the themes screen with memory loaded
- **THEN** no PouchDB read is issued for it

#### Scenario: Typing in the words filter
- **WHEN** the learner types in the words filter
- **THEN** no PouchDB read is issued and the rows follow each keystroke without a debounce

### Requirement: A screen appears with its data within one frame of the tap
Every transition between screens — home to words, lesson or themes; words to lesson; and closing any screen back to home — SHALL put the target screen, with the data it shows, in the page before the first animation frame after the tap. The first rows of the words screen, the first trial of a lesson, the tiles of the themes screen and the state of home SHALL be on screen then, not a placeholder. Work that the transition starts but the screen does not need to show — the rest of the first page of words, the synchronisation pull on entry — SHALL run after the screen is painted.

#### Scenario: Opening the words screen
- **WHEN** the learner taps «Список слов» on home with memory loaded
- **THEN** the words screen and its first rows are in the page when the next animation frame runs

#### Scenario: Starting a lesson
- **WHEN** the learner taps «Начать урок»
- **THEN** the lesson and its first trial are in the page when the next animation frame runs

#### Scenario: Opening the themes screen
- **WHEN** the learner opens the themes screen with memory loaded
- **THEN** its tiles are in the page when the next animation frame runs

#### Scenario: Closing a screen
- **WHEN** the learner closes the words, lesson or themes screen
- **THEN** home, with its data, is in the page when the next animation frame runs

### Requirement: A screen left does not come back
Once the learner has left a screen, nothing finishing later — a write, a pull, a feed change — SHALL put that screen back on display.

#### Scenario: Leaving the words screen at once
- **WHEN** the learner opens the words screen and closes it immediately
- **THEN** home stays on display and the words screen does not return

### Requirement: The app opens on a splash until memory is loaded
The app SHALL show the server's splash, and nothing else, until memory holds everything user-db held at start; then the screen asked for SHALL replace it, with every word's retention, and nothing SHALL be shown between the splash and that screen. When reading the learner's data at start fails, the app SHALL say so in place of the splash and ask for a reload, and SHALL NOT read it again.

#### Scenario: Opening the app
- **WHEN** the app is opened
- **THEN** the server's splash is on display until memory is loaded
- **AND** the screen asked for replaces it then, retention included, with nothing shown in between

#### Scenario: The data cannot be read
- **WHEN** reading the learner's data fails at start
- **THEN** «Не получается прочитать данные на устройстве. Перезагрузите страницу.» is shown in place of the splash
- **AND** nothing reads the data again until the page is reloaded

### Requirement: Deleting a word keeps its reviews and examples
Deleting a word SHALL delete the word document and take its id out of collections as `specs/collections-data-model/spec.md` states. Which collections list it SHALL be asked of memory once it has caught up. When PouchDB refuses any document of that write, the delete SHALL read them again and write once more; refused again, the delete SHALL fail: it SHALL be reported, and a word that still exists SHALL be listed in every collection that listed it.

Deleting a word SHALL NOT delete its reviews or its examples (a collection keeps its examples as `specs/collections-data-model/spec.md` states): when a deleted word is added again, its reviews and its examples SHALL come back with it as its history. A word added again SHALL get no initial review when it has reviews, and an example SHALL be fetched for it only when the active collection has none. After a synchronisation pass, a word or a collection the pass deleted SHALL get no example fetched for it.

#### Scenario: No collection keeps a deleted word
- **WHEN** a word is deleted, and another device has just put it into a collection memory has not seen
- **THEN** no collection lists the word once the delete is done

#### Scenario: A delete PouchDB refuses twice
- **WHEN** PouchDB refuses the deletion of the word twice
- **THEN** the delete is reported as failed
- **AND** the word exists and is listed in its collections as before

#### Scenario: A word added again
- **WHEN** the learner deletes a word and adds it again
- **THEN** the word has the reviews it had before the delete and no new one, and its retention is computed from them
- **AND** no example is fetched for it in «Всё подряд», where it has one already

#### Scenario: A word re-created on another device
- **WHEN** a word deleted here earlier arrives again by a pull, with reviews made on another device
- **THEN** those reviews and the earlier ones are kept, on this start and on every later one

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

### Requirement: The learner's data is held in memory as a projection of user-db
The client SHALL hold in app state what the screens show of the learner's data: every word and phrase with its search text normalised once, the reviews of each word, the collections, and the account's examples. Every one of them lives in user-db, so memory SHALL be a projection of user-db alone, and SHALL neither read nor follow device-db. PouchDB SHALL remain the only source of that data, and memory SHALL hold nothing PouchDB lacks, so that losing memory — a reload, a frozen tab — loses nothing: it is rebuilt from PouchDB.

Memory SHALL be loaded at start in one go, from a snapshot when one passes its checks (Requirement: A repeat start reads memory from a snapshot). Otherwise the client SHALL note where user-db's change feed stands, read every document of user-db, then read what it stored meanwhile, and only then put memory in place. From there it SHALL follow user-db's change feed and take every change in the order the database stored it — this app's own writes, another tab's and a synchronisation's alike. Because a live feed can drop a change without reporting it, memory SHALL catch up each time the page becomes visible again, and before a decision that needs every collection: it SHALL read what the database stored after the last change it took. Memory SHALL record the feed position of the last change that changed it: the change's sequence and its document's id and revision. A change that the feed or a catch-up brings at or below that sequence SHALL change nothing, and a batch that changes nothing memory keeps, such as a pairing receipt, SHALL leave memory as it was, so that nothing renders for it. For each id, memory SHALL hold the last document the database stored, which is the winner replication picked. Memory SHALL be the same whatever order the load's documents arrive in and however they are grouped: a document whose revision memory already holds SHALL change nothing, a deleted document SHALL leave memory, a review MAY arrive before its word, and a document memory cannot take SHALL be left out and logged without stopping the rest, and the version of it that memory held SHALL leave memory, so that memory holds what a full read gives.

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
- **THEN** memory is unchanged

#### Scenario: A deletion
- **WHEN** a word, review, collection or example is deleted in PouchDB
- **THEN** it is no longer in memory

#### Scenario: Any order, one memory
- **WHEN** the load's pages arrive in any order and the documents stored since the load began follow them in the order they were stored
- **THEN** memory equals the memory built from the final documents at once
