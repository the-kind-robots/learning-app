# vocabulary-list-paging Specification

## Purpose
What order the word list is in, how many rows it puts on screen, what a page costs to
read, how the next page is asked for, and what resets or preserves the loaded count.
## Requirements
### Requirement: The word list is ordered alphabetically

The word list SHALL show words in alphabetical order, by the normalised form the word is
stored under: case and umlaut spelling do not split the order, and no other ranking — how
due a word is, how well it is remembered — decides where a row sits. The order SHALL NOT
be German dictionary collation; the stored order is the order, and nothing is read to
improve on it.

#### Scenario: Words of different retention

- **WHEN** the list holds words that were last reviewed days and months apart
- **THEN** they are shown in alphabetical order, whatever their retention levels

#### Scenario: The lesson is not affected

- **WHEN** a lesson is started
- **THEN** it draws the words most in need of review, not the alphabetically first ones

### Requirement: The first page does not cost the whole vocabulary

The first page of the word list SHALL be read as a page: the rows it shows and their
retention levels, not every word in scope and not every review. The time to the first row
SHALL therefore not grow with the number of words the vocabulary holds.

#### Scenario: A page of a large vocabulary

- **WHEN** the words screen is opened on a vocabulary of thousands of words
- **THEN** one page of words is read from storage
- **AND** reviews are read only for the words on that page

#### Scenario: Counting the vocabulary

- **WHEN** the screen needs to know whether the vocabulary is empty
- **THEN** it reads the count without reading the words

#### Scenario: A search reads what it has to

- **WHEN** a search query is active
- **THEN** every word in scope is examined, since a substring can sit anywhere in a value
  or a translation
- **AND** retention levels are still read only for the rows the page shows

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

The word list SHALL append the next page of rows as the reader approaches the end of the
rendered rows, without any control to press. The approach SHALL be measured against the
box that scrolls the list, so the next page is asked for while the end of the rows is
still below the fold and the reader meets rows rather than a wait. The rows already
rendered SHALL stay.

#### Scenario: Approaching the end

- **WHEN** the reader has scrolled to within a short distance of the end of the rendered
  rows and unrendered rows remain
- **THEN** the next page is asked for, while the end of the rows is still off screen

#### Scenario: Scrolling to the end

- **WHEN** the reader scrolls to the end of the rendered rows and unrendered rows remain
- **THEN** the next 50 rows are appended to the list
- **AND** the rows that were already there are still there

#### Scenario: The last page

- **WHEN** the reader reaches the end of the rows and no unrendered rows remain
- **THEN** nothing is appended

#### Scenario: The list is rebuilt under the reader

- **WHEN** a render replaces the list rather than appending to it, while the reader stays
  on the screen
- **THEN** approaching the end still appends the next page

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
The return to the top SHALL happen when the matching rows replace the rows on screen, not
when the query is typed — until they arrive the reader is still reading the old rows.

#### Scenario: Typing after loading several pages

- **WHEN** several pages are loaded and the reader types a query matching more than one
  page of words
- **THEN** the list renders one page of matching rows

#### Scenario: Typing while scrolled to the bottom

- **WHEN** the reader is at the bottom of the loaded rows and changes the query
- **THEN** the list is scrolled back to its first row once the matching rows are on screen
- **AND** the next page is not appended until the reader reaches the bottom again

#### Scenario: Scrolling on while the query is still being read

- **WHEN** the reader changes the query and scrolls back down before the matching rows
  have arrived
- **THEN** the list is at its first row once those rows replace the old ones
- **AND** the reader's position at that moment does not append a second page

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

### Requirement: The newest read of the list is the one that writes it

The list SHALL take its rows, its query and its loaded count from the read requested most
recently, and SHALL discard a read that was requested earlier and answered later. Several
reads can be in flight at once — the next page, a search, the reload that follows an edit
or a synchronisation pull — and the order they are answered in is not the order they were
asked in.

#### Scenario: A page answered after a search

- **WHEN** a page of the list is asked for and the reader types a query before it arrives,
  and that page is answered after the matching rows
- **THEN** the list holds the matching rows
- **AND** the query the list holds is the one the reader typed

#### Scenario: The search box and the rows agree

- **WHEN** a read that was overtaken is discarded
- **THEN** the query in the search box is still the query the rows on screen were read
  under

#### Scenario: A reload overtaking a page

- **WHEN** a reload of the list is asked for while a page request is still in flight
- **THEN** the answer to the earlier request does not replace the rows the later one
  brought

### Requirement: A page arriving in the background leaves the open word open

Rows arriving for the list SHALL NOT close the open word. The word edit dialog SHALL
close when the reader cancels it, when a change to the word is saved, and when the word
is removed.

#### Scenario: A page arrives while a word is open

- **WHEN** the reader opens a word while a page of rows is being read
- **THEN** the dialog is still open when those rows arrive
- **AND** what the reader had typed into it is still there

#### Scenario: Saving a change

- **WHEN** the reader saves a change to the open word
- **THEN** the dialog closes

#### Scenario: Cancelling

- **WHEN** the reader cancels the dialog
- **THEN** the dialog closes

#### Scenario: Removing the word

- **WHEN** the reader removes the open word and confirms the removal
- **THEN** the dialog closes

#### Scenario: Declining the removal

- **WHEN** the reader asks to remove the open word and does not confirm
- **THEN** the dialog is still open

#### Scenario: Coming back to the screen

- **WHEN** the reader leaves the words screen with a word open and comes back to it
- **THEN** no word is open

