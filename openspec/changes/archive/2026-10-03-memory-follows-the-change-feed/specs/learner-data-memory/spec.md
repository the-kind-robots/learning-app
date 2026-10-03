## MODIFIED Requirements

### Requirement: The learner's data is held in memory as a projection of PouchDB
The client SHALL hold in app state what the screens show of the learner's data: every word and phrase with its search text normalised once, the reviews of each word, the collections, and the examples this device holds. PouchDB SHALL remain the only source of that data, and memory SHALL hold nothing PouchDB lacks, so that losing memory — a reload, a frozen tab — loses nothing: it is rebuilt from PouchDB.

Memory SHALL be loaded at start in one go: the client SHALL note where each database's change feed stands, read every document of both databases, then read what they stored meanwhile, and only then put memory in place. From there it SHALL follow each database's change feed and take every change in the order the database stored it — this app's own writes, another tab's and a synchronisation's alike. Because a live feed can drop a change without reporting it, memory SHALL catch up each time the page becomes visible again, and before a decision that needs every collection: it SHALL read what the databases stored after the last change it took. For each id, memory SHALL hold the last document the database stored, which is the winner replication picked. Memory SHALL be the same whatever order the load's documents arrive in and however they are grouped: a document whose revision memory already holds SHALL change nothing, a deleted document SHALL leave memory, a review MAY arrive before its word, and a document memory cannot take SHALL be left out and logged without stopping the rest.

#### Scenario: Loaded at start
- **WHEN** the app starts on a device holding words, reviews, collections and examples
- **THEN** memory holds every one of them once the load completes

#### Scenario: A write during the load
- **WHEN** a document is written after the load began and before it completes
- **THEN** memory holds that document once the load completes

#### Scenario: A change the feed dropped
- **WHEN** a document is stored and the change feed does not bring it
- **THEN** memory holds it once the page becomes visible again

#### Scenario: An edit that wins over a deletion
- **WHEN** a word deleted on one device at a higher revision is edited on another device at a lower one, and replication makes the edit the winner
- **THEN** memory holds the edited word

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
A write made by this app SHALL go to PouchDB first. A write PouchDB refuses SHALL leave memory untouched, so a screen never shows data that will not replicate. Once PouchDB has accepted it, the write SHALL read the documents it wrote back from PouchDB and memory SHALL take them before the write completes, so that the screen that asked for it can show it at once; the change feed bringing them later SHALL change nothing. A write SHALL NOT wait for the change feed.

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

#### Scenario: A word with two branches
- **WHEN** replication leaves a word with two branches and the learner edits the word
- **THEN** the edit is put over the winning revision, and the other branch stays as replication left it

#### Scenario: A pull and an add of the same word
- **WHEN** a pull stores a word with one translation, and before memory holds it the learner adds the same word with another translation
- **THEN** the stored word has both translations

## ADDED Requirements

### Requirement: The app opens on a splash until memory is loaded
The app SHALL show a splash, and no screen, until memory holds everything the databases held at start; then the screen asked for SHALL be shown, with every word's retention. When the learner's data cannot be read, the splash SHALL say so, and the app SHALL keep trying to read it.

#### Scenario: Opening the app
- **WHEN** the app is opened
- **THEN** a splash is on display until memory is loaded
- **AND** the screen asked for is shown then, retention included

#### Scenario: The data cannot be read
- **WHEN** reading the learner's data fails at start
- **THEN** the splash says «Не получается прочитать данные на устройстве. Пробуем снова…»
- **AND** the screen asked for is shown once a read succeeds

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

## REMOVED Requirements

### Requirement: The app opens on a splash until the words and collections are read
**Reason**: Memory is loaded in one go behind the splash; see "The app opens on a splash until memory is loaded".
**Migration**: None.
