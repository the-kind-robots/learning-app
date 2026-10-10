## ADDED Requirements

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

## REMOVED Requirements

### Requirement: The learner's data is held in memory as a projection of PouchDB

**Reason**: Restated under a header that names the one database memory follows, without the task
queue and the per-pass backfill, which are gone.
**Migration**: Requirement: The learner's data is held in memory as a projection of user-db.
