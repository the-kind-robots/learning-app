## ADDED Requirements

### Requirement: The word list renders one page of rows

The word list SHALL render at most one page of rows — 50 — however many words the active
scope holds, and SHALL NOT put the rest of the vocabulary in the document.

#### Scenario: A vocabulary larger than one page

- **WHEN** the words screen is opened with more than 50 words in the active scope
- **THEN** 50 word rows are rendered
- **AND** the remaining words are absent from the document

#### Scenario: A vocabulary smaller than one page

- **WHEN** the words screen is opened with fewer than 50 words in the active scope
- **THEN** every word is rendered

### Requirement: Reaching the bottom appends the next page

The word list SHALL append the next page of rows when the reader reaches the end of the
rendered rows, without any control to press. The rows already rendered SHALL stay.

#### Scenario: Scrolling to the end

- **WHEN** the reader scrolls to the end of the rendered rows and unrendered rows remain
- **THEN** the next 50 rows are appended to the list
- **AND** the rows that were already there are still there

#### Scenario: The last page

- **WHEN** the reader reaches the end of the rows and no unrendered rows remain
- **THEN** nothing is appended

### Requirement: The end-of-list sentinel exists only while rows remain

The list SHALL render a sentinel element after the last row while unrendered rows remain,
and SHALL NOT render it once every row that matches the current query is on screen.

#### Scenario: More rows remain

- **WHEN** the rendered rows are fewer than the rows matching the current query
- **THEN** the list ends with a sentinel element

#### Scenario: Every row is rendered

- **WHEN** every row matching the current query is rendered
- **THEN** no sentinel element is in the list

### Requirement: A search starts again at the first page

Changing the search query SHALL return the list to a single page of matching rows and to
the top of that page, whatever was loaded and however far down the reader had scrolled.

#### Scenario: Typing after loading several pages

- **WHEN** several pages are loaded and the reader types a query matching more than one
  page of words
- **THEN** the list renders one page of matching rows

#### Scenario: Typing while scrolled to the bottom

- **WHEN** the reader is at the bottom of the loaded rows and changes the query
- **THEN** the list is scrolled back to its first row
- **AND** the next page is not appended until the reader reaches the bottom again

#### Scenario: Clearing the query

- **WHEN** the reader clears the query
- **THEN** the list renders one page of the unfiltered rows

### Requirement: A reload keeps the loaded rows

Reloading the list SHALL keep the number of rows the reader had loaded and the query they
were read under, so the reader is not returned to the first page — whether the reload
follows their own edit or arrives on its own after synchronisation brought documents.

#### Scenario: Editing a word from a later page

- **WHEN** the reader has loaded more than one page and saves a change to a word
- **THEN** the list still holds the rows that were loaded

#### Scenario: Deleting a word from a later page

- **WHEN** the reader has loaded more than one page and removes a word
- **THEN** the list still holds the rows that were loaded, less the removed one

#### Scenario: A sync pull reloads the screen

- **WHEN** a synchronisation pass brings documents while the reader has several pages
  loaded
- **THEN** the reload asks for the rows that were loaded, under the query they were read
  under
- **AND** the list is not returned to its first page

### Requirement: The presenter decides whether another page exists

`pages.words.presenter` SHALL derive the loaded row count and whether unrendered rows
remain, and hand the view ready props. The view SHALL NOT compare counts. The vocabulary
use case SHALL report the number of rows left after the search filter, since the
pre-filter `:total` cannot answer whether another page exists.

#### Scenario: Rows are capped by the limit

- **WHEN** the presenter is given fewer rows than the filter matched
- **THEN** it reports that more rows remain

#### Scenario: Every matching row is present

- **WHEN** the presenter is given as many rows as the filter matched
- **THEN** it reports that no rows remain

#### Scenario: The use case reports the filtered count

- **WHEN** the vocabulary list is asked for a page of a filtered list
- **THEN** it returns the rows of that page, the pre-filter total, and the number of rows
  the filter matched
