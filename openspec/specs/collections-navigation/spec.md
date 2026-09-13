# collections-navigation Specification

## Purpose
Define the home-screen collections grid UI: grid icon, collections page, collection cards, create/rename/delete flows, and active-collection switching.

## Requirements

### Requirement: Home screen shows collections grid icon
The system SHALL display a 2×2 grid icon in the top-right area of the home screen header.

#### Scenario: Grid icon visible
- **WHEN** the home screen is rendered
- **THEN** a collections grid icon button is visible in the top-right corner

### Requirement: Tapping collections icon opens collections page
The system SHALL navigate to the collections page when the collections icon is tapped.

#### Scenario: Navigate to collections page
- **WHEN** the user taps the collections grid icon
- **THEN** the app navigates to `:page/collections`
- **AND** the page renders all existing collections as tiles and the floating «+» button

### Requirement: Collections page shows collection cards
The system SHALL render one tile per collection, or one folder tile per group of collections sharing a folder key. Each tile SHALL display the collection name and word count. The All Words tile SHALL always be present and listed first.

#### Scenario: Collection card displays name and word count
- **WHEN** the collections page is rendered
- **THEN** each tile shows the collection name and the count of words belonging to that collection

#### Scenario: All Words card always listed first
- **WHEN** the collections page is rendered
- **THEN** the All Words tile appears first

### Requirement: Tapping a collection card switches the active collection
The system SHALL switch the active collection and navigate back to the home screen when a collection card is tapped.

#### Scenario: Tap collection card activates it
- **WHEN** the user taps a collection card
- **THEN** that collection becomes the active collection
- **AND** the active collection is persisted to localStorage
- **AND** the app navigates back to `:page/home`

### Requirement: Long-press on named collection card enters editing mode
The system SHALL enter an inline editing state with Rename and Delete affordances when the user long-presses a named collection card. Long-press SHALL have no effect on the All Words card.

#### Scenario: Long-press on named collection enters editing
- **WHEN** the user long-presses a named collection card
- **THEN** that card enters an editing state exposing rename input and a delete control

#### Scenario: Long-press has no effect on All Words
- **WHEN** the user long-presses the All Words card
- **THEN** no editing state is entered

### Requirement: Delete collection confirms before removing
The system SHALL show a confirmation dialog before deleting a collection. The dialog text SHALL be "Удалить набор «X»? Слова останутся в «Все слова»." where X is the collection name.

#### Scenario: Confirm delete removes collection
- **WHEN** the user invokes Delete and confirms the dialog
- **THEN** the collection is deleted
- **AND** all word-collection memberships for that collection are removed
- **AND** the grid refreshes without the deleted collection

#### Scenario: Cancel delete leaves collection intact
- **WHEN** the user invokes Delete but cancels the dialog
- **THEN** the collection is not deleted

### Requirement: Rename collection updates collection name
The system SHALL allow the user to rename a named collection via inline editing.

#### Scenario: Rename updates stored collection name
- **WHEN** the user edits the card name and submits a non-blank value
- **THEN** the collection document is updated with the new name
- **AND** the card reflects the new name

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
When the browser cancels a touch on a collection card (`pointercancel` with no `click` following), the card SHALL take it as a tap if the pointer travelled at most 10 px since it went down and the page scrolled at most 2 px in the meantime. A gesture SHALL fire at most once: a `click` the browser delivers after such a recovered cancel does nothing.

#### Scenario: Cancelled tap without movement
- **WHEN** the user taps a collection card and the browser cancels the touch after at most 10 px of movement and at most 2 px of page scroll
- **THEN** the collection is activated as if the tap had completed

#### Scenario: Cancelled touch that moved
- **WHEN** the browser cancels a touch on a card after it travelled more than 10 px or the page scrolled more than 2 px
- **THEN** nothing is activated

### Requirement: The themes screen reads collection documents only
Opening the themes screen SHALL read the collection documents and the count of words, and SHALL NOT read vocabulary or review documents.

#### Scenario: No vocabulary or review read on open
- **WHEN** the user opens the themes screen
- **THEN** the collection documents and the words count are read
- **AND** no vocabulary document and no review document is read

### Requirement: Collections are tiles in a masonry, alphabetical by rows
The system SHALL render every collection as a tile showing its name and its word count, with a colour accent cycled by position over the eight-colour palette, the active one tinted in its accent. Tiles SHALL be placed into two columns under 700 px of viewport width and four from 700 px, each tile into the currently shortest column, in alphabetical order — locale-aware and case-insensitive — so the order reads by rows left to right. «Всё подряд» SHALL be a tile pinned first with the total words count.

#### Scenario: Alphabetical by rows
- **WHEN** the themes screen renders collections `b`, `A`, `c` in two columns
- **THEN** the first row holds «Всё подряд» and `A`, the second `b` and `c`

#### Scenario: Twelve collections on a phone
- **WHEN** the themes screen renders twelve collections without folders on a 384 × 800 viewport
- **THEN** every one of them is visible without scrolling

### Requirement: A slash in a name makes a folder
The system SHALL render collections whose names share the text before the first `/` — trimmed of surrounding whitespace, compared case-insensitively — as one folder tile: a header with the folder key and a row per child, alphabetical, showing the rest of the name after the first `/`, trimmed, with any deeper `/` kept in the row text. A folder with one child is still a folder. Folder tiles sort by their key among plain tiles. The header and every row SHALL be tappable and SHALL activate their collection.

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
The header of a folder SHALL stand for the collection whose name equals the folder key (trimmed, case-insensitive) and SHALL show the size of the union of that collection's words and every child's, without duplicates. When no such collection exists, the header SHALL show the union of the children, and tapping it SHALL create the collection named by the key, make it active and return home.

#### Scenario: Header count is the union
- **WHEN** `Kurs` holds words a and b, `Kurs / Kapitel 1` holds b and c, `Kurs / Kapitel 2` holds d
- **THEN** the header `Kurs` shows 4

#### Scenario: Tapping a header without a document
- **WHEN** `Grammatik / Konnektoren` exists and no collection is named `Grammatik`
- **THEN** tapping the header `Grammatik` creates the collection `Grammatik`, makes it active and returns home

### Requirement: A floating button creates a collection
The system SHALL show a floating «+» button fixed at the bottom right of the themes screen, always on screen, which opens the name prompt; confirming with a non-blank name creates the collection. The grid SHALL keep a bottom inset so the button covers no tile.

#### Scenario: Creating from the floating button
- **WHEN** the user taps the floating «+» and confirms a non-blank name
- **THEN** the collection is created and appears as a tile
