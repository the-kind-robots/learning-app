## ADDED Requirements

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
