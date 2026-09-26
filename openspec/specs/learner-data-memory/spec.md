# learner-data-memory Specification

## Purpose
Define how the learner's data is held in memory as a projection of PouchDB, and how screens answer from it within one frame of the tap.

## Requirements

### Requirement: The learner's data is held in memory as a projection of PouchDB
The client SHALL hold in app state what the screens show of the learner's data: every word and phrase with its search text normalised once, the reviews of each word, the collections, and the examples this device holds. PouchDB SHALL remain the only source of that data, and memory SHALL hold nothing PouchDB lacks, so that losing memory — a reload, a frozen tab — loses nothing: it is rebuilt from PouchDB.

Memory SHALL be loaded at start from the databases as they stand at a recorded update sequence, and SHALL then follow each database's change feed from that sequence, so that no document written during the load is missed. A document arriving through the feed whose revision memory already holds SHALL change nothing; a deleted document SHALL leave memory.

#### Scenario: Loaded at start
- **WHEN** the app starts on a device holding words, reviews, collections and examples
- **THEN** memory holds every one of them once the load completes

#### Scenario: A write during the load
- **WHEN** a document is written after the update sequence was recorded and before the load completes
- **THEN** memory holds that document once the feed has caught up

#### Scenario: The same revision twice
- **WHEN** the feed delivers a document at the revision memory already holds
- **THEN** memory is unchanged and nothing renders

#### Scenario: A deletion
- **WHEN** a word, review, collection or example is deleted in PouchDB
- **THEN** it is no longer in memory

### Requirement: Memory takes a write only after PouchDB accepted it
A write made by this app SHALL go to PouchDB first, and memory SHALL take the written document, at its new revision, only after PouchDB has accepted it. A write PouchDB refuses SHALL leave memory untouched, so a screen never shows data that will not replicate.

#### Scenario: Own write, then a screen
- **WHEN** the learner adds or edits a word and then opens a screen that shows it
- **THEN** the screen shows the word as written

#### Scenario: A refused write
- **WHEN** PouchDB refuses a write
- **THEN** memory holds what it held before the write

### Requirement: Writes made elsewhere reach the open screen
A document written in PouchDB by anything other than this tab's own write path — another tab of the app, a synchronisation pull from another device — SHALL reach memory through the change feed, and the screen on display SHALL show it without being opened again.

#### Scenario: Another tab adds a word
- **WHEN** the words screen is on display in one tab and a word is added in another tab of the app
- **THEN** the word appears on the words screen of the first tab

#### Scenario: A pull brings a word from another device
- **WHEN** the words screen is on display and a synchronisation pull brings a word added on another device
- **THEN** the word appears on the words screen

### Requirement: Screens answer from memory, not from storage
Opening home, words, lesson or the themes screen, and typing in the words filter, SHALL read no document from PouchDB; each SHALL compute what it shows from memory.

#### Scenario: Opening a screen
- **WHEN** the learner opens home, words, lesson or the themes screen with memory loaded
- **THEN** no PouchDB read is issued for it

#### Scenario: Typing in the words filter
- **WHEN** the learner types in the words filter
- **THEN** no PouchDB read is issued and the rows follow each keystroke without a debounce

### Requirement: A screen appears with its data within one frame of the tap
Every transition between screens — home to words, lesson or themes; words to lesson; and closing any screen back to home — SHALL put the target screen, with the data it shows, in the page before the first animation frame after the tap. The rows of the words screen, the first trial of a lesson, the tiles of the themes screen and the state of home SHALL be on screen then, not a placeholder. Work that the transition starts but the screen does not need to show — writing the lesson document, ending the lesson being left, the synchronisation pull on entry — SHALL run after the screen is painted.

#### Scenario: Opening the words screen
- **WHEN** the learner taps «Список слов» on home with memory loaded
- **THEN** the words screen and its rows are in the page when the next animation frame runs

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

### Requirement: A screen opened before memory is ready fills in when it is
A screen opened before memory has loaded SHALL open at once, showing no claim about the data it cannot yet read — the words screen shows neither the empty-vocabulary invitation nor the no-matches message, the themes screen its loading state, a lesson no trial — and SHALL fill in when memory is ready, without being opened again.

#### Scenario: The words screen opened during the load
- **WHEN** the app is opened at the words screen and memory is not loaded yet
- **THEN** the screen neither says the vocabulary is empty nor that nothing matched
- **AND** its rows appear when memory is ready, without navigation
