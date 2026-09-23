# collections-navigation Specification

## Purpose
Define how the user reaches the themes screen, what a tap on a tile does, and how a collection is created, renamed and deleted.

## Requirements

### Requirement: Home screen shows collections grid icon
The system SHALL display a 2×2 grid icon in the top-right area of the home screen header.

#### Scenario: Grid icon visible
- **WHEN** the home screen is rendered
- **THEN** a collections grid icon button is visible in the top-right corner

### Requirement: Tapping collections icon opens collections page
The system SHALL navigate to the collections page when the collections icon is tapped.

#### Scenario: Navigate to the themes screen
- **WHEN** the user taps the collections grid icon
- **THEN** the themes screen is shown
- **AND** it renders all existing collections as tiles and the floating «+» button

### Requirement: Tapping a collection card switches the active collection
The system SHALL switch the active collection and navigate back to the home screen when a collection card is tapped.

#### Scenario: Tap collection card activates it
- **WHEN** the user taps a collection card
- **THEN** that collection becomes the active collection
- **AND** the app returns to the home screen

### Requirement: A long press reveals the delete control, which deletes at once
The system SHALL put a named collection's tile, folder header or folder row into an editing state when the user long-presses it, revealing a delete control on it; tapping that control SHALL delete the collection with no confirmation step. A long press SHALL have no effect on the «Всё подряд» tile, which carries no delete control.

#### Scenario: Long press on a named collection reveals the control
- **WHEN** the user long-presses a named collection's tile
- **THEN** that tile enters an editing state showing a delete control labelled «Удалить набор «X»» for the collection named X

#### Scenario: The delete control deletes
- **WHEN** the user taps the delete control on a tile in the editing state
- **THEN** the collection is deleted and the screen re-renders without it

#### Scenario: Long press has no effect on «Всё подряд»
- **WHEN** the user long-presses the «Всё подряд» tile
- **THEN** no editing state is entered and no delete control appears

### Requirement: A collection is renamed on the home screen heading
The system SHALL let the user rename the active collection by editing the home screen's heading. A name another collection already carries — trimmed, case-insensitive — SHALL be refused the way a blank one is: the heading shows the current name again and no document is written.

#### Scenario: Rename updates the stored name
- **WHEN** the user edits the home screen heading and submits a non-blank value no other collection carries
- **THEN** the collection document is updated with the new name
- **AND** the heading and the collection's tile show it

#### Scenario: Rename to a taken name is refused
- **WHEN** the user submits a name another collection carries
- **THEN** the heading shows the current name again
- **AND** the collection document is unchanged

### Requirement: Opening the themes screen switches to it at once
The system SHALL switch to the themes screen the moment it is opened and SHALL show a loading state there until its collections are available. A reload of the screen that is already open (after a sync pull) SHALL keep the current collections on screen until the new ones arrive, with no loading state in between.

#### Scenario: First open shows loading before the collections
- **WHEN** the user opens the themes screen
- **THEN** the screen is on display with a loading state before its collections have been read
- **AND** the collections replace the loading state once they are available

#### Scenario: A post-pull reload keeps the current content
- **WHEN** the themes screen is on display and a sync pull completes
- **THEN** the current collections stay on screen until the reloaded ones replace them
- **AND** no loading state is shown in between

### Requirement: Collection cards own their touch gestures
A collection card SHALL take a long press as its own gesture: pressing a card SHALL NOT select its preview text or open the browser's callout, and a double tap on a card SHALL NOT zoom the page. Panning and pinching the screen stay with the browser.

#### Scenario: Long press on a card
- **WHEN** the user presses and holds a collection card on a touch screen
- **THEN** the card enters its editing state and no text on it is selected

#### Scenario: Quick double tap on a card
- **WHEN** the user taps a collection card twice in quick succession
- **THEN** the page does not zoom

### Requirement: A tap the browser cancels without movement still opens the collection
When the browser cancels a touch on a collection card (`pointercancel` with no `click` following), the card SHALL judge it when the finger lifts, not when the browser cancels it: it is a tap if the finger travelled at most 10 px from where it went down and the page scrolled at most 2 px, both measured over the whole touch up to the lift. A touch the browser cancels and then ends without a lift (`touchcancel`) activates nothing. A long press SHALL give up when the finger travels more than 10 px, whether that movement comes before or after the cancel. A gesture SHALL fire at most once: a `click` the browser delivers after such a recovered tap does nothing.

#### Scenario: Cancelled tap without movement
- **WHEN** the user taps a collection card and the browser cancels the touch
- **AND** the finger lifts after at most 10 px of travel and at most 2 px of page scroll
- **THEN** the collection is activated as if the tap had completed

#### Scenario: A swipe that starts on a card
- **WHEN** the user puts a finger on a collection card and swipes to scroll the screen
- **AND** the browser cancels the touch before the page has scrolled and before any pointer movement was reported
- **THEN** the screen scrolls and nothing is activated

#### Scenario: Cancelled touch that moved
- **WHEN** the browser cancels a touch on a card and the finger travels more than 10 px or the page scrolls more than 2 px before it lifts
- **THEN** nothing is activated

### Requirement: The themes screen reads collection documents only
Opening the themes screen SHALL read the collection documents and the count of words, and SHALL NOT read vocabulary or review documents.

#### Scenario: No vocabulary or review read on open
- **WHEN** the user opens the themes screen
- **THEN** the collection documents and the words count are read
- **AND** no vocabulary document and no review document is read

### Requirement: Collections are tiles in a masonry, one alphabetical sequence down the columns
The system SHALL render every collection as a tile showing its name and its word count, with a colour accent cycled by position over the eight-colour palette, the active one tinted in its accent. The tiles SHALL be one alphabetical sequence — locale-aware and case-insensitive — laid out in two columns under 700 px of viewport width and four from 700 px, read down each column in turn, and no tile SHALL be broken across a column boundary. «Всё подряд» SHALL be a tile pinned first with the total words count.

#### Scenario: Alphabetical down the columns
- **WHEN** the themes screen renders collections `b`, `A`, `c` in two columns
- **THEN** the first column holds «Всё подряд» and `A`, the second `b` and `c`

#### Scenario: A tile is never split by a column break
- **WHEN** the themes screen renders its tiles in columns
- **THEN** every tile is one unbroken box in one column

#### Scenario: Twelve collections on a phone
- **WHEN** the themes screen renders twelve collections without folders on a 384 × 800 viewport
- **THEN** every one of them is visible without scrolling

### Requirement: A slash in a name makes a folder
The system SHALL render collections whose names share the text before the first `/` — trimmed of surrounding whitespace, compared case-insensitively — as one folder tile: a header with the folder key and a row per child, alphabetical, showing the rest of the name after the first `/`, trimmed, with any deeper `/` kept in the row text. A folder with one child is still a folder. Folder tiles sort by their key among plain tiles. Every row SHALL be tappable and SHALL activate its collection; the header is the requirement below.

#### Scenario: Two chapters under one course
- **WHEN** collections `Kurs / Kapitel 1` and `Kurs / Kapitel 2` exist
- **THEN** one tile shows the header `Kurs` and the rows `Kapitel 1` and `Kapitel 2`

#### Scenario: Deeper slashes stay in the row
- **WHEN** a collection `Kurs / A / B` exists
- **THEN** it is the row `A / B` under the folder `Kurs`

#### Scenario: Tapping a row
- **WHEN** the user taps the row `Kapitel 1`
- **THEN** `Kurs / Kapitel 1` becomes the active collection and the app returns home

### Requirement: The folder header is the parent collection
The header of a folder SHALL stand for the collection whose name equals the folder key (trimmed, case-insensitive) and SHALL show the size of the union of that collection's words and every child's, without duplicates; it is tappable and tinted when active like any tile. When no such collection exists, the header SHALL be a plain group label — a small grey uppercase caption with the folder key and, in the tile's count style, the size of the union of the children's words without duplicates — that is not a tap target, is never tinted and creates nothing. Creating the parent is the user's job through the «+» prompt; once its document exists the header stands for it on the next load.

#### Scenario: Header count is the union
- **WHEN** `Kurs` holds words a and b, `Kurs / Kapitel 1` holds b and c, `Kurs / Kapitel 2` holds d
- **THEN** the header `Kurs` shows 4

#### Scenario: A header without a document is a label with the children's count
- **WHEN** `Grammatik / Konnektoren` holds words e and f and no collection is named `Grammatik`
- **THEN** the themes screen shows the caption `Grammatik` with the count 2
- **AND** tapping it does nothing and no document named `Grammatik` is written

#### Scenario: Creating the parent through «+»
- **WHEN** `Grammatik / Konnektoren` exists and the user creates `Grammatik` through the «+» prompt
- **THEN** the header `Grammatik` becomes the collection's tile showing the union of its children

### Requirement: Every target on the themes screen is reachable by keyboard
Every tap target on the themes screen — a collection tile, a folder header standing for a collection, and a folder row — SHALL be reachable by keyboard, in the same order the screen reads. The reached target SHALL open its collection when activated from the keyboard, exactly as a tap opens it. A folder label standing for no collection of its own is no target and SHALL be skipped.

#### Scenario: Keyboard walks the reading order
- **WHEN** «Всё подряд», the folder `Grammatik` with its one row `Konnektoren`, the folder `Kurs` with the rows `Kapitel 1` and `Kapitel 2`, and the collection `Solo` are on the themes screen
- **AND** the user moves focus forward from the top of the screen
- **THEN** focus reaches «Всё подряд», `Konnektoren`, the header `Kurs`, `Kapitel 1`, `Kapitel 2` and `Solo`, in that order
- **AND** the label `Grammatik` is never focused

#### Scenario: Opening the focused collection
- **WHEN** focus is on the tile `Solo`
- **AND** the user activates it from the keyboard
- **THEN** `Solo` becomes the active collection and the app returns home

### Requirement: A floating button creates a collection
The system SHALL show a floating «+» button fixed at the bottom right of the themes screen, always on screen, which opens the name prompt; confirming with a non-blank name creates the collection. The grid SHALL keep a bottom inset so the button covers no tile.

#### Scenario: Creating from the floating button
- **WHEN** the user taps the floating «+» and confirms a non-blank name
- **THEN** the collection is created and appears as a tile

### Requirement: The delete mark takes the place of the count
In editing mode the delete mark (✕) of a tile, a folder header or a folder row SHALL stand where that target's word count stands, and the count SHALL be hidden while the mark is shown. The mark SHALL NOT overlap the name, and the name SHALL keep the same width and the same place in its tile in and out of editing mode.

#### Scenario: A row with a long name in editing mode
- **WHEN** the row `Meetings und Besprechungen mit Kollegen` under the folder `Arbeit` is in editing mode on a 384 × 800 viewport
- **THEN** the ✕ stands where the row's count was, the count is not shown, and the ✕ does not overlap the name

#### Scenario: Entering editing mode moves no text
- **WHEN** a tile, a folder header or a folder row enters editing mode
- **THEN** its name keeps its width and its place in the tile

### Requirement: Long collection names break at syllables
A collection name SHALL be marked as German and, where a word does not fit its line, SHALL break at a syllable boundary with a hyphen. A string with no hyphenation point SHALL still break rather than overflow its tile. «Всё подряд» is not a collection name and is not marked German.

#### Scenario: A long German word
- **WHEN** a folder row shows `Unterkunftsmöglichkeiten` on a 384 × 800 viewport and the word does not fit one line
- **THEN** it breaks at a syllable with a hyphen at the end of the first line

#### Scenario: An unbreakable string
- **WHEN** a collection name is a string longer than its tile with no hyphenation point
- **THEN** the name wraps within its tile and nothing overflows
