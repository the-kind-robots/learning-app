## ADDED Requirements

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

## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: Collections page has a create-collection card
**Reason**: The dashed «+» card scrolled away with the grid; a floating button is always on screen.
**Migration**: The floating «+» button opens the same name prompt.
