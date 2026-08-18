# home-add-form-focus Delta

## ADDED Requirements

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
- **THEN** the unfiltered layout-shift score stays under 0.05
