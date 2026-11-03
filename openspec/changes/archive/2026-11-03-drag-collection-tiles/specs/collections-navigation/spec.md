## RENAMED Requirements

- FROM: `### Requirement: Collections are tiles in a masonry, one alphabetical sequence down the columns`
- TO: `### Requirement: Collections are tiles in a masonry, in the learner's order down the columns`

## MODIFIED Requirements

### Requirement: Collections are tiles in a masonry, in the learner's order down the columns
The system SHALL render every collection as a tile showing its name and its word count, the active one tinted in its accent. The tiles SHALL be one sequence laid out in two columns under 700 px of viewport width and four from 700 px, read down each column in turn, and no tile SHALL be broken across a column boundary. From 700 px the grid SHALL take as many columns as `n` tiles fill in the rows four columns need, counting a tile as one row — `ceil(n / ceil(n / 4))` — each as wide as a column of the four-column grid, and SHALL stand centred horizontally on the page. «Всё подряд» SHALL be a tile pinned first with the total words count.

The tiles after «Всё подряд» SHALL stand by their position — a folder tile by the lowest position among its collections. Tiles with no position SHALL stand before every positioned tile; tiles with no position, and tiles with equal positions, SHALL stand alphabetically among themselves, locale-aware and case-insensitive. A new collection SHALL take a position after every other, so its tile stands last unless it joins a folder that exists.

Each tile SHALL carry an accent cycled over the eight-colour palette by the order the tiles were made in, «Всё подряд» taking the first: a folder tile counts as made when the earliest of its collections was, and tiles made together count alphabetically. A tile SHALL keep its accent when it moves.

#### Scenario: Alphabetical down the columns
- **WHEN** the themes screen renders collections `b`, `A`, `c` that were never moved, in two columns
- **THEN** the first column holds «Всё подряд» and `A`, the second `b` and `c`

#### Scenario: A new collection stands last
- **WHEN** collections `B` and `C` exist and the user creates `A`
- **THEN** the tiles read «Всё подряд», `B`, `C`, `A`

#### Scenario: A moved tile keeps its colour
- **WHEN** the user drags the last tile before the first collection
- **THEN** every tile has the accent it had before the drag

#### Scenario: A tile is never split by a column break
- **WHEN** the themes screen renders its tiles in columns
- **THEN** every tile is one unbroken box in one column

#### Scenario: Twelve collections on a phone
- **WHEN** the themes screen renders twelve collections without folders on a 384 × 800 viewport
- **THEN** every one of them is visible without scrolling

#### Scenario: Few tiles on a desktop stand in the middle
- **WHEN** the themes screen renders «Всё подряд» and one collection on a 1280 px wide viewport
- **THEN** the two tiles stand side by side, and the space left of the first equals the space right of the second

#### Scenario: Five tiles on a desktop leave no column empty
- **WHEN** the themes screen renders five tiles of one row each on a 1280 px wide viewport
- **THEN** they stand in three columns, and the space left of the grid equals the space right of it

### Requirement: A slash in a name makes a folder
The system SHALL render collections whose names share the text before the first `/` — trimmed of surrounding whitespace, compared case-insensitively — as one folder tile: a header with the folder key and a row per child, alphabetical, showing the rest of the name after the first `/`, trimmed, with any deeper `/` kept in the row text. A folder with one child is still a folder. A folder tile stands among plain tiles by the order of the requirement above. Every row SHALL be tappable and SHALL activate its collection; the header is the requirement below.

#### Scenario: Two chapters under one course
- **WHEN** collections `Kurs / Kapitel 1` and `Kurs / Kapitel 2` exist
- **THEN** one tile shows the header `Kurs` and the rows `Kapitel 1` and `Kapitel 2`

#### Scenario: Deeper slashes stay in the row
- **WHEN** a collection `Kurs / A / B` exists
- **THEN** it is the row `A / B` under the folder `Kurs`

#### Scenario: Tapping a row
- **WHEN** the user taps the row `Kapitel 1`
- **THEN** `Kurs / Kapitel 1` becomes the active collection and the app returns home

## ADDED Requirements

### Requirement: A tile is dragged to a new place
A tile other than «Всё подряд» SHALL be dragged when the pointer, still down, travels more than 10 px from where it went down: on a touch screen after a long press has put a target of the tile in its editing state, or from a target already in its editing state; with a mouse at once. A folder tile SHALL be dragged by any of its targets and SHALL move as one, its rows keeping their order. While a tile is dragged it SHALL follow the pointer, the page SHALL NOT scroll with the pointer's movement, and a caret SHALL show where it lands: above or below the tile nearest the pointer, never before «Всё подряд». While the pointer is within 56 px of the window's top or bottom edge, the page SHALL scroll toward that edge on its own, faster the nearer the pointer, until the page ends or the pointer leaves that band; the tile and the caret SHALL follow the scroll. On release the tile SHALL stand at the caret, the editing state SHALL end, and no collection SHALL be activated. The new order SHALL be kept in the collections' positions — `0`, `1`, `2`, … in the order of the tiles after «Всё подряд», every collection of a folder taking its tile's — so that it holds on every device the learner's data reaches. A tile released where it stood SHALL write nothing.

#### Scenario: A mouse drag
- **WHEN** collections `A`, `B`, `C` stand in that order and the user drags `C` with the mouse above `A`
- **THEN** the tiles read «Всё подряд», `C`, `A`, `B`
- **AND** they read so after the screen is opened again

#### Scenario: A long press that moves
- **WHEN** the user presses `C` on a touch screen until it shows its ✕ and, without lifting, moves the finger above `A`
- **THEN** the page does not scroll and the tiles read «Всё подряд», `C`, `A`, `B` after the lift

#### Scenario: A tile in its editing state
- **WHEN** `C` shows its ✕ and the user puts a finger on it and moves it above `A`
- **THEN** the tiles read «Всё подряд», `C`, `A`, `B` after the lift

#### Scenario: A folder moves as one
- **WHEN** the user drags the row `Kapitel 2` of the folder `Kurs` above a plain tile
- **THEN** the folder tile stands before that tile, its rows `Kapitel 1` and `Kapitel 2` in order

#### Scenario: «Всё подряд» stays first
- **WHEN** the user drags a tile above «Всё подряд»
- **THEN** «Всё подряд» stays first and the dragged tile stands second

#### Scenario: A tile held at the top edge scrolls the page
- **WHEN** the screen is scrolled to its last tile and the user drags that tile and holds the finger at the top edge
- **THEN** the page scrolls to its start on its own
- **AND** the tile let go above the first collection stands second
