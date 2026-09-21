## MODIFIED Requirements

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

## ADDED Requirements

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
