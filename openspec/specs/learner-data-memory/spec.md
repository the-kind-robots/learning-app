# learner-data-memory Specification

## Purpose
Define how the learner's data is held in memory as a projection of PouchDB, and how screens answer from it within one frame of the tap.

## Requirements

### Requirement: The learner's data is held in memory as a projection of PouchDB
The client SHALL hold in app state what the screens show of the learner's data: every word and phrase with its search text normalised once, the reviews of each word, the collections, and the examples this device holds. PouchDB SHALL remain the only source of that data, and memory SHALL hold nothing PouchDB lacks, so that losing memory — a reload, a frozen tab — loses nothing: it is rebuilt from PouchDB.

Memory SHALL be loaded at start in two parts — first the words and the collections, then the reviews and the examples — and SHALL then follow each database's change feed from where the database stood before the load began, so that no document written during the load is missed. Memory SHALL be the same whatever order documents arrive in and however they are grouped: a document whose revision memory already holds SHALL change nothing, a deleted document SHALL leave memory, and a review MAY arrive before its word.

#### Scenario: Loaded at start
- **WHEN** the app starts on a device holding words, reviews, collections and examples
- **THEN** memory holds every one of them once the load completes

#### Scenario: A write during the load
- **WHEN** a document is written after the load began and before it completes
- **THEN** memory holds that document once the feed has caught up

#### Scenario: The same revision twice
- **WHEN** the feed delivers a document at the revision memory already holds
- **THEN** memory is unchanged

#### Scenario: A deletion
- **WHEN** a word, review, collection or example is deleted in PouchDB
- **THEN** it is no longer in memory

#### Scenario: Any order, one memory
- **WHEN** the same final documents arrive in any order, in any batches, with repeats and deletions along the way
- **THEN** memory equals the memory built from the final documents at once

### Requirement: Memory takes a write only after PouchDB accepted it
A write made by this app SHALL go to PouchDB first, and memory SHALL take the written document, at its new revision, only after PouchDB has accepted it. A write PouchDB refuses SHALL leave memory untouched, so a screen never shows data that will not replicate.

#### Scenario: Own write, then a screen
- **WHEN** the learner adds or edits a word and then opens a screen that shows it
- **THEN** the screen shows the word as written

#### Scenario: A refused write
- **WHEN** PouchDB refuses a write
- **THEN** memory holds what it held before the write

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

### Requirement: The app opens on a splash until the words and collections are read
The app SHALL show a splash, and no screen, until the words and the collections are in memory; from then on adding a word, switching collections and every screen SHALL be available. The reviews and the examples SHALL load after the splash is gone. Until the reviews are in memory, the words screen SHALL list its words with a neutral retention mark in place of each word's retention, and a lesson opened in that time SHALL be drawn once they are.

#### Scenario: Opening the app
- **WHEN** the app is opened
- **THEN** a splash is on display until the words and the collections are in memory
- **AND** the screen asked for is shown then

#### Scenario: The words screen before the reviews are read
- **WHEN** the words screen is opened after the splash and before the reviews are in memory
- **THEN** its words are listed with a neutral retention mark
- **AND** each word's retention is shown once the reviews are in memory
