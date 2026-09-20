## ADDED Requirements

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
