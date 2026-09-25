# home-add-form-focus Specification

## Purpose
Define how the home add-word form behaves under keyboard and touch: focus after submit, the autocomplete suggestion list and its keyboard navigation, the translation field, and the shortcut that starts a lesson.

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

### Requirement: The phone suggestion list is as tall as its rows

On a phone the suggestion list SHALL occupy the height of the rows it holds,
up to a ceiling. It SHALL NOT reserve a fixed height, so one row is one row
high and the box has no emptiness under its last suggestion. Beyond the
ceiling the list SHALL scroll inside itself rather than grow.

#### Scenario: One or two suggestions

- **WHEN** the list on a phone is down to one or two rows
- **THEN** its rendered height equals its content height and is well under the
  ceiling

#### Scenario: More rows than fit

- **WHEN** the dictionary answers with more rows than the ceiling allows
- **THEN** the list stops at the ceiling and scrolls inside itself

### Requirement: The list's ceiling bounds how far it moves the form

The phone suggestion list SHALL open in the page flow, and its ceiling SHALL be
chosen as the maximum displacement acceptable for the controls below it: the
translation field and the submit button move down by the list's height plus its
margin, and by no more than the ceiling plus that margin. The value field being
typed into and the panel heading above it SHALL NOT move.

#### Scenario: Typing a word with two matches

- **WHEN** a word matching two lemmas is typed into the value field on a phone
- **THEN** the submit button moves down by exactly the list's height plus its
  4 px margin, and by no more than 180 px
- **AND** the value field and the panel heading stay where they were

#### Scenario: Layout shift stays within budget

- **WHEN** the word is typed at human cadence and the list opens in flow
- **THEN** the unfiltered layout-shift score stays under 0.06 — the budget of a
  content-sized list, which resizes whenever the match count changes, and not
  the 0.05 of the fixed-height list that opened once and never resized

### Requirement: The home screen starts a lesson from the keyboard

`Alt`+`Enter` (`Option`+`Enter` on a Mac) on the home screen SHALL start the
lesson — the same action the `НАЧАТЬ УРОК` button performs. The handler SHALL
belong to the home page, so the keystroke works wherever the focus sits on that
screen and has no meaning on any other screen. The keystroke's default action
SHALL be suppressed, so it starts one lesson. When the vocabulary is empty the
lesson footer is hidden, and the keystroke SHALL do nothing.

#### Scenario: Alt+Enter from the word field

- **WHEN** the home screen is open with a non-empty vocabulary
- **AND** the German word field has focus
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** the lesson screen opens
- **AND** no word is added

#### Scenario: Alt+Enter from the translation field

- **WHEN** the home screen is open with a non-empty vocabulary
- **AND** the translation field has focus
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** the lesson screen opens
- **AND** the form is not submitted and the field takes no newline

#### Scenario: Alt+Enter outside the fields

- **WHEN** the home screen is open with a non-empty vocabulary
- **AND** focus is on one of the screen's buttons rather than on a field
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** the lesson screen opens

#### Scenario: An empty vocabulary offers no lesson

- **WHEN** the home screen is open with an empty vocabulary, so the lesson
  footer is hidden
- **AND** the user presses `Enter` while `Alt` is held
- **THEN** nothing happens and the user stays on the home screen

#### Scenario: The screen's other keystrokes keep their meaning

- **WHEN** the user presses `Enter` on the German word field, `Ctrl`+`Enter` or
  `Cmd`+`Enter` on the translation field, or `Escape` with suggestions open
- **THEN** each keeps the behaviour it already had — picking the highlighted
  suggestion or moving on to the translation, submitting the form, dismissing
  the suggestions

### Requirement: The suggestion list shows which entry the keyboard is on

While the suggestion list is open it SHALL mark exactly one entry as the
active one, and that mark SHALL be visible on screen. The marked entry SHALL
be the entry `Enter` and `Tab` pick, so what the list shows and what a pick
returns can never disagree. A list that has just appeared SHALL have its first
entry marked, before any arrow is pressed.

#### Scenario: A list opens with its first entry marked

- **WHEN** the dictionary answers a prefix and the suggestion list appears
- **THEN** exactly one entry carries the active mark
- **AND** it is the first entry in the list

#### Scenario: The mark says what Enter will pick

- **WHEN** the suggestion list is open with an entry marked active
- **AND** the user presses `Enter`
- **THEN** the entry that was marked is the one applied to the form

### Requirement: The arrows move the visible mark

`ArrowDown` and `ArrowUp` on the German word input SHALL move the active mark
by one entry and SHALL suppress the keystroke's default action. `ArrowDown`
SHALL stop on the last entry and `ArrowUp` on the first — neither wraps. After
each keystroke exactly one entry SHALL carry the mark, and the marked entry
SHALL be scrolled into view, so a list taller than its box follows the
selection instead of leaving it off screen.

#### Scenario: ArrowDown moves the mark down

- **WHEN** the suggestion list is open with an entry marked
- **AND** the user presses `ArrowDown`
- **THEN** the mark is on the next entry down
- **AND** no other entry carries it

#### Scenario: ArrowUp moves the mark back

- **WHEN** the mark has been moved down the list
- **AND** the user presses `ArrowUp`
- **THEN** the mark is on the entry above the one it was on

#### Scenario: The ends of the list hold

- **WHEN** the mark is on the last entry and the user presses `ArrowDown`, or
  it is on the first entry and the user presses `ArrowUp`
- **THEN** the mark stays where it is

#### Scenario: A mark below the fold is scrolled to

- **WHEN** an arrow moves the mark onto an entry outside the list's visible
  rows
- **THEN** the list scrolls that entry into view

### Requirement: A failed save says so and keeps the input
When saving from the home add form fails, the form SHALL show, under its fields,
«Слово не сохранилось: в приложении сбой, и это не ваша ошибка.» for a word,
«Фраза не сохранилась: в приложении сбой, и это не ваша ошибка.» for a phrase,
whatever the cause, and SHALL keep the typed value and translation. Every add
error SHALL carry text in the same place; a border alone is not enough. A save
that succeeds SHALL clear the text.

#### Scenario: The write fails
- **WHEN** the user submits a word and a translation and the write fails
- **THEN** the form shows «Слово не сохранилось: в приложении сбой, и это не ваша ошибка.»
- **AND** the word and translation fields still hold what was typed

#### Scenario: A later save succeeds
- **WHEN** the error text is on display and the user submits again and the write succeeds
- **THEN** the error text is gone and the form is reset for the next word
