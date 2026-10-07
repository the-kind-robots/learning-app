## REMOVED Requirements

### Requirement: Enter on the German word input picks the highlighted suggestion
**Reason**: Its scenario "Enter without an active suggestion submits the form" states a behaviour the form never had since the phrase flow (#535): Enter with no list moves on to the translation field. A scenario cannot be renamed in place, so the requirement is restated under a name that covers both halves.
**Migration**: See "Enter on the German word input picks the highlighted suggestion or moves on" below.

## ADDED Requirements

### Requirement: Enter on the German word input picks the highlighted suggestion or moves on
The system SHALL apply the highlighted autocomplete suggestion when the user presses Enter while the suggestions dropdown is open and has an active item. Default form submission SHALL be suppressed in this case. With no suggestion list on screen, Enter SHALL move focus to the translation field and SHALL NOT submit the form.

#### Scenario: Enter confirms the active suggestion
- **WHEN** the suggestions dropdown is visible
- **AND** an item is active (selected via ArrowDown/ArrowUp or auto-highlighted)
- **AND** the user presses Enter while the German input has focus
- **THEN** default form submission is prevented
- **AND** the input value is set to the active suggestion's lemma
- **AND** the suggestions dropdown closes
- **AND** focus moves to the translation input

#### Scenario: Enter without a suggestion list moves to the translation field
- **WHEN** no suggestion list is on screen
- **AND** the user presses Enter while the German input has focus
- **THEN** default form submission is prevented
- **AND** focus moves to the translation input
- **AND** the form is not submitted

## MODIFIED Requirements

### Requirement: Typing keeps the previous suggestion list until the next answer
The system SHALL keep the currently displayed suggestion list while the user types in the German word input, replacing it only when the dictionary delivers the answer for the new prefix. A keystroke SHALL NOT blank the list, but it SHALL drop, at once and without a query, every row that no longer completes what the field holds. A row still completes it while the normalised typed text is a prefix of the row's lemma, with or without a leading definite article, or of one of the word's forms that matched the prefix the row was answered for — the answer carries those forms per row, so `das Haus` answered for `Häu` through `Häuser` stays while the field reads `Häus`, and `der Rücken` stays while it reads `rücke`. The comparison SHALL normalise as the dictionary query does (case, umlauts, ß). The rows that still match SHALL keep their order and the marked entry when it is among them; a dropped row SHALL NOT be on screen, tappable, or picked by Enter or Tab.

#### Scenario: A further keystroke does not blank the list
- **WHEN** a suggestion list is visible
- **AND** the user types another character that every row's lemma still starts with
- **THEN** the previous list stays visible through the debounce and lookup
- **AND** the dictionary's answer for the new prefix replaces it

#### Scenario: Typing past a row drops it before any answer
- **WHEN** the list for `Rücken` holds `der Rücken`
- **AND** the user types `k`
- **THEN** `der Rücken` is gone from the list before the dictionary answers for `Rückenk`
- **AND** no query was needed to remove it

#### Scenario: A dropped row cannot be picked
- **WHEN** a row has been dropped by further typing
- **AND** the user taps where it was, or presses Enter or Tab
- **THEN** the word field keeps what was typed
- **AND** the translation field is not filled from that row

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

### Requirement: The translation field takes several lines and submits on a modifier
The translation field SHALL accept a multi-line translation. A bare `Enter` SHALL insert a newline and SHALL NOT submit the form; `Ctrl`+`Enter` or `Cmd`+`Enter` SHALL submit it. The submit button SHALL keep submitting the form, since it is the only path on a phone, where neither modifier can be typed. `Enter` on the German word input keeps its own meaning and SHALL NOT be affected.

#### Scenario: Enter inserts a newline
- **WHEN** the translation field has focus
- **AND** the user presses `Enter` with no modifier
- **THEN** the form is not submitted
- **AND** the field takes a newline

#### Scenario: Ctrl+Enter submits
- **WHEN** the translation field has focus
- **AND** the user presses `Enter` while `Ctrl` is held
- **THEN** the form is submitted
- **AND** the keystroke's default action is suppressed, so the form is submitted once

#### Scenario: Cmd+Enter submits
- **WHEN** the translation field has focus
- **AND** the user presses `Enter` while `Cmd` (meta) is held
- **THEN** the form is submitted

#### Scenario: The button submits without a keyboard
- **WHEN** the user activates the add button
- **THEN** the form is submitted, whatever the translation field holds

#### Scenario: The German input keeps its Enter
- **WHEN** the German word input has focus
- **AND** the user presses `Enter`
- **THEN** the existing behaviour applies — the highlighted suggestion is picked, or focus moves to the translation field when there is none
