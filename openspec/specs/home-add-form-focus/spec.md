# home-add-form-focus Specification

## Purpose
TBD - created by archiving change restore-home-add-form-focus. Update Purpose after archive.
## Requirements
### Requirement: Successful desktop add-word submit restores primary input focus
The system SHALL restore focus to the German word input after a successful home add-word submit on desktop-style pointer devices.

#### Scenario: Desktop repeated entry stays in the German input
- **WHEN** a user successfully submits the home add-word form on a desktop-style pointer device
- **THEN** the form is reset through the normal success flow
- **AND** focus returns to the `#new-word-value` input
- **AND** the focus restoration is implemented as a Replicant lifecycle hook, not an htmx event handler

### Requirement: Mobile submit does not force focus restoration
The system SHALL avoid forcing focus restoration on touch-first/mobile devices after a successful home add-word submit.

#### Scenario: Mobile success does not reopen the keyboard
- **WHEN** a user successfully submits the home add-word form on a coarse-pointer or touch-first device
- **THEN** the success flow completes without explicitly focusing the German input
- **AND** the implementation does not intentionally reopen the soft keyboard

### Requirement: Enter on the German word input picks the highlighted suggestion
The system SHALL apply the highlighted autocomplete suggestion when the user presses Enter while the suggestions dropdown is open and has an active item. Default form submission SHALL be suppressed in this case.

#### Scenario: Enter confirms the active suggestion
- **WHEN** the suggestions dropdown is visible
- **AND** an item is active (selected via ArrowDown/ArrowUp or auto-highlighted)
- **AND** the user presses Enter while the German input has focus
- **THEN** default form submission is prevented
- **AND** the input value is set to the active suggestion's lemma
- **AND** the suggestions dropdown closes
- **AND** focus moves to the translation input

#### Scenario: Enter without an active suggestion submits the form
- **WHEN** the dropdown is closed or has no active item
- **AND** the user presses Enter
- **THEN** the form submits normally (no interception)

### Requirement: Blur on the German word input dismisses the suggestions
The system SHALL clear the suggestions dropdown when the German word input loses focus, without racing the click that picks a suggestion.

#### Scenario: Clicking outside dismisses suggestions
- **WHEN** the suggestions dropdown is visible
- **AND** the user clicks outside the input and the suggestion list
- **THEN** the input loses focus
- **AND** the suggestions dropdown is cleared

#### Scenario: Clicking a suggestion picks it
- **WHEN** the suggestions dropdown is visible
- **AND** the user clicks a suggestion item
- **THEN** the suggestion is applied (no race with the blur clear)
- **AND** the input value is set to the lemma
- **AND** focus moves to the translation input

### Requirement: Form buttons display a visible focus ring
The system SHALL render a visible focus indicator on every interactive button reachable by keyboard from the home screen — including `ДОБАВИТЬ`, `НАЧАТЬ УРОК`, `Список слов`, and the collections grid corner icon.

#### Scenario: Tabbing through the form shows focus on each button
- **WHEN** a keyboard user tabs through the home screen
- **THEN** each button receives a visible focus ring while focused
- **AND** the focus ring uses the project accent color (or an equally contrasting outline) so it is clearly distinguishable from the unfocused state

### Requirement: Typing keeps the previous suggestion list until the next answer
The system SHALL keep the currently displayed suggestion list while the user types in the German word input, replacing it only when the dictionary delivers the answer for the new prefix. A keystroke SHALL NOT blank the list.

#### Scenario: A further keystroke does not blank the list
- **WHEN** a suggestion list is visible
- **AND** the user types another character
- **THEN** the previous list stays visible through the debounce and lookup
- **AND** the dictionary's answer for the new prefix replaces it

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

### Requirement: Suggestions feel instant after a typing pause
Suggestions SHALL appear within ~150 ms of a typing pause, and continuous typing SHALL NOT trigger a lookup per keystroke. Changes to this budget SHALL cite a measurement, not a guess.

#### Scenario: Pause after typing
- **WHEN** the user stops typing
- **THEN** suggestions appear within the budget

#### Scenario: Fast typing
- **WHEN** keystrokes arrive faster than the debounce
- **THEN** lookups coalesce and the visible list never blanks between answers

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
- **THEN** the existing behaviour applies — the highlighted suggestion is picked, or the form submits when there is none

### Requirement: The prefilled translation is the dictionary's text as stored
When the dictionary fills the translation field — on a suggestion arriving for an untouched field, or on the user picking a suggestion — the field SHALL receive the dictionary's translation text as the dictionary holds it. The transport splits a lemma's translations on `,` and leaves the following space on the next piece, so the form SHALL trim the pieces before rejoining them on `, `. What the field shows is what gets stored, so the reconstruction SHALL neither drop nor double a space.

#### Scenario: A stored translation containing a comma is prefilled whole
- **WHEN** the dictionary's translation for a lemma is `без того, чтобы`
- **AND** that lemma's suggestion fills the translation field
- **THEN** the field holds `без того, чтобы`, with no doubled space after the comma

#### Scenario: Several translations stay readable
- **WHEN** a lemma has the translations `пёс` and `собака`
- **THEN** the field holds `пёс, собака`

#### Scenario: Picking a suggestion fills the same text
- **WHEN** the user picks a suggestion from the list
- **THEN** the translation field receives that entry's translation text by the same rule

