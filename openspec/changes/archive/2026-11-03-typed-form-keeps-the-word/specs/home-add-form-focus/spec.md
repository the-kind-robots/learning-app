## MODIFIED Requirements

### Requirement: Typing keeps the previous suggestion list until the next answer
The system SHALL keep the currently displayed suggestion list while the user types in the German word input, replacing it only when the dictionary delivers the answer for the new prefix. A keystroke SHALL NOT blank or shrink the list: every row stays on screen, but a row that no longer completes what the field holds is inert at once and without a query. A row still completes it while the normalised typed text is a prefix of the row's lemma, with or without a leading definite article, or of one of the word's forms that matched the prefix the row was answered for — the answer carries those forms per row, so `das Haus` answered for `Häu` through `Häuser` stays while the field reads `Häus`, and `der Rücken` stays while it reads `rücke`. The comparison SHALL normalise as the dictionary query does (case, umlauts, ß). All rows SHALL keep their order. The entry the user marked with an arrow stays marked while it still completes the field; otherwise the mark follows the rule of "The suggestion list shows which entry the keyboard is on". An inert row SHALL be dimmed and marked `aria-disabled`, and SHALL NOT be tappable, picked by Enter or Tab, or counted for add-word or mode detection; with no marked entry Enter moves on to the translation field.

#### Scenario: A further keystroke does not blank the list
- **WHEN** a suggestion list is visible
- **AND** the user types another character that every row's lemma still starts with
- **THEN** the previous list stays visible through the debounce and lookup
- **AND** the dictionary's answer for the new prefix replaces it

#### Scenario: Typing past a row makes it inert before any answer
- **WHEN** the list for `Rücken` holds `der Rücken` and `der Rückenkurs`
- **AND** the user types `k`
- **THEN** both rows are still on screen before the dictionary answers for `Rückenk`
- **AND** `der Rücken` is inert and `der Rückenkurs` is marked

#### Scenario: An inert row cannot be picked
- **WHEN** a row has become inert through further typing
- **AND** the user taps it, or presses Enter or Tab with no completing row left
- **THEN** the word field keeps what was typed
- **AND** the translation field is not filled from that row

#### Scenario: A longer list does not shrink between answers
- **WHEN** the list holds ten rows and the user types a letter only one of them completes
- **THEN** ten rows stay on screen through the keystroke
- **AND** the answer for the new prefix replaces them

#### Scenario: Typing on through an inflected form keeps the row
- **WHEN** the list for `Häu` holds `das Haus`, matched through `Häuser`, or the list for `ging` holds `gehen`, matched through `ging`, `gingen`, `gingst`, `gingt`
- **AND** the user types on to `Häus`, or to `gingst`
- **THEN** the row stays through the keystroke and the answer
- **AND** the list does not flash

#### Scenario: Emptying the input clears the list
- **WHEN** a suggestion list is visible
- **AND** the user empties the input
- **THEN** the list is cleared once the dictionary answers with no completions for the empty prefix

#### Scenario: Picking from a kept list uses the entry's own data
- **WHEN** the visible list still belongs to a previous prefix
- **AND** the user picks an entry (click, Enter, or Tab)
- **THEN** the word and translation are filled from that entry's own lemma and translations
- **AND** the list is cleared

#### Scenario: Submit still clears the list
- **WHEN** the add-word form submits successfully
- **THEN** the form reset clears the suggestion list along with the inputs

### Requirement: The suggestion list shows which entry the keyboard is on

While the suggestion list is open it SHALL mark at most one entry as the
active one, and that mark SHALL be visible on screen. The marked entry SHALL
be the entry `Enter` and `Tab` pick, so what the list shows and what a pick
returns can never disagree. Before any arrow is pressed the mark SHALL be on
the first entry whose lemma, with or without its article, the typed text
begins. An entry that completes the field only through one of its word forms
SHALL NOT be marked until an arrow moves the mark onto it: `Enter` or `Tab`
then keeps the typed form in the field. With no entry marked, the translation
field SHALL NOT be filled from the list, and either arrow SHALL mark the first
entry that completes the field.

#### Scenario: A list opens with its first entry marked

- **WHEN** the dictionary answers a prefix that begins the lemma of the first entry
- **THEN** exactly one entry carries the active mark
- **AND** it is the first entry in the list

#### Scenario: The mark says what Enter will pick

- **WHEN** the suggestion list is open with an entry marked active
- **AND** the user presses `Enter`
- **THEN** the entry that was marked is the one applied to the form

#### Scenario: A typed word form keeps the typed word

- **WHEN** the user types `gesondert` and the list shows `sondern`, matched only through the form `gesondert`
- **THEN** no entry carries the active mark
- **AND** the translation field is not filled from `sondern`
- **AND** pressing `Enter` or `Tab` leaves `gesondert` in the word field

#### Scenario: An arrow picks the lemma of a typed form

- **WHEN** the user types `gesondert` and the list shows `sondern` with no entry marked
- **AND** the user presses `ArrowDown`, then `Enter`
- **THEN** the word field holds `sondern` and the translation field its translation
