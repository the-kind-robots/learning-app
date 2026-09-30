## MODIFIED Requirements

### Requirement: The word list renders one page of rows

The word list SHALL render at most one page of rows — 50 — however many words the active
scope holds, and SHALL NOT put the rest of the vocabulary in the document. Opening the
screen or changing the query SHALL put its first 20 rows in the document in the task of
the tap or the keystroke — what renders within one frame — and the rest of the page by
the frame after.

#### Scenario: A vocabulary larger than one page

- **WHEN** the words screen is opened with more than 50 words in the active scope
- **THEN** 20 word rows are in the document when the first animation frame after the tap runs
- **AND** 50 word rows are rendered once that frame is painted
- **AND** the remaining words are absent from the document

#### Scenario: A vocabulary smaller than one page

- **WHEN** the words screen is opened with fewer than 50 words in the active scope
- **THEN** every word is rendered

### Requirement: A search starts again at the first page

Changing the search query SHALL return the list to a single page of matching rows and to
the top of that page, whatever was loaded and however far down the reader had scrolled.
The matching rows SHALL replace the rows on screen on the keystroke that changed the
query, and the list SHALL return to its top at the same moment.

#### Scenario: Typing after loading several pages

- **WHEN** several pages are loaded and the reader types a query matching more than one
  page of words
- **THEN** the list renders one page of matching rows

#### Scenario: Typing while scrolled to the bottom

- **WHEN** the reader is at the bottom of the loaded rows and changes the query
- **THEN** the list shows the matching rows from its first row
- **AND** the next page is not appended until the reader reaches the bottom again

#### Scenario: Scrolling on while the query is still being read

- **WHEN** the reader changes the query and scrolls back down right after
- **THEN** the matching rows were already on screen from their first row when the
  scrolling began
- **AND** the reader's position does not append a second page until they reach the
  bottom of the matching rows

#### Scenario: Clearing the query

- **WHEN** the reader clears the query
- **THEN** the list renders one page of the unfiltered rows

### Requirement: A reload keeps the loaded rows

The reader's own edit or removal on the words screen SHALL keep the number of rows the
reader had loaded and the query they were read under, so the reader is not returned to
the first page. A change arriving on its own — from another tab, or after
synchronisation brought documents — SHALL NOT change the list on display.

#### Scenario: Editing a word from a later page

- **WHEN** the reader has loaded more than one page and saves a change to a word
- **THEN** the list still holds the rows that were loaded

#### Scenario: Deleting a word from a later page

- **WHEN** the reader has loaded more than one page and removes a word
- **THEN** the list still holds the rows that were loaded, less the removed one

#### Scenario: A sync pull reloads the screen

- **WHEN** a synchronisation pass brings documents while the reader has several pages
  loaded
- **THEN** the list on display does not change
- **AND** the reader is not returned to its first page

### Requirement: A page arriving in the background leaves the open word open

Rows changing for the list — a page appended, a change to the learner's data arriving
while the screen is open — SHALL NOT close the open word. The word edit dialog SHALL
close when the reader cancels it, when a change to the word is saved, and when the word
is removed.

#### Scenario: A page arrives while a word is open

- **WHEN** the reader has a word open and the list's rows change
- **THEN** the dialog is still open
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

## REMOVED Requirements

### Requirement: The first page does not cost the whole vocabulary
**Reason**: The list no longer reads storage: it is cut from the learner's data in memory, so no page read exists whose cost could grow with the vocabulary (`learner-data-memory`).
**Migration**: None — "Screens answer from memory, not from storage" and the one-frame requirement bound the cost instead.

### Requirement: The newest read of the list is the one that writes it
**Reason**: The list issues no reads; each change to the query, the loaded count or the learner's data computes the rows synchronously, so no read can be overtaken.
**Migration**: None.
