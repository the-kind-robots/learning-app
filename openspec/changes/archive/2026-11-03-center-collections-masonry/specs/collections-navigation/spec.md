## MODIFIED Requirements

### Requirement: Collections are tiles in a masonry, one alphabetical sequence down the columns
The system SHALL render every collection as a tile showing its name and its word count, with a colour accent cycled by position over the eight-colour palette, the active one tinted in its accent. The tiles SHALL be one alphabetical sequence — locale-aware and case-insensitive — laid out in two columns under 700 px of viewport width and four from 700 px, read down each column in turn, and no tile SHALL be broken across a column boundary. From 700 px the grid SHALL take as many columns as `n` tiles fill in the rows four columns need, counting a tile as one row — `ceil(n / ceil(n / 4))` — each as wide as a column of the four-column grid, and SHALL stand centred horizontally on the page. «Всё подряд» SHALL be a tile pinned first with the total words count.

#### Scenario: Alphabetical down the columns
- **WHEN** the themes screen renders collections `b`, `A`, `c` in two columns
- **THEN** the first column holds «Всё подряд» and `A`, the second `b` and `c`

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
