## RENAMED Requirements

- FROM: `### Requirement: A long press reveals the delete control, which deletes at once`
- TO: `### Requirement: A long press reveals the delete control, which asks before deleting`

## MODIFIED Requirements

### Requirement: A long press reveals the delete control, which asks before deleting
The system SHALL put a named collection's tile, folder header or folder row into an editing state when the user long-presses it, revealing a delete control on it; tapping that control SHALL ask «Удалить набор «X»? Слова останутся в словаре.» for the collection named X, and SHALL delete the collection only when the user agrees. Declining SHALL leave the collection and its editing state as they were. A long press SHALL have no effect on the «Всё подряд» tile, which carries no delete control.

#### Scenario: Long press on a named collection reveals the control
- **WHEN** the user long-presses a named collection's tile
- **THEN** that tile enters an editing state showing a delete control labelled «Удалить набор «X»» for the collection named X

#### Scenario: The delete control deletes
- **WHEN** the user taps the delete control on a tile in the editing state
- **AND** agrees to the question
- **THEN** the collection is deleted

#### Scenario: Declining keeps the collection
- **WHEN** the user taps the delete control and declines the question
- **THEN** the collection and its tile stay as they were

#### Scenario: Long press has no effect on «Всё подряд»
- **WHEN** the user long-presses the «Всё подряд» tile
- **THEN** no editing state is entered and no delete control appears

### Requirement: Opening the themes screen switches to it at once
The system SHALL switch to the themes screen the moment it is opened, with its tiles. A collection the learner creates on the screen SHALL be shown at once, and one the learner deletes there SHALL turn into its trace at once; one that arrives from elsewhere SHALL show the next time the screen is opened.

#### Scenario: First open shows loading before the collections
- **WHEN** the user opens the themes screen
- **THEN** the screen is on display with its tiles, with no loading state

#### Scenario: A post-pull reload keeps the current content
- **WHEN** the themes screen is on display and a sync pull brings a collection
- **THEN** the tiles on display do not change
- **AND** the collection is among the tiles once the screen is opened again

## ADDED Requirements

### Requirement: A deleted collection leaves a trace that brings it back
A collection deleted on the themes screen SHALL leave a trace in the place of its tile, folder header or folder row: one button labelled «Вернуть набор «X»», showing the name struck through where the name stood and an undo mark where the count stood. The name SHALL wrap as it did, and the other targets SHALL keep their places. Keyboard focus SHALL move to the trace and the status line SHALL announce «Набор «X» удалён». Activating the trace SHALL write the collection back with its id, name and words, the trace SHALL become its target again with focus on it, and the status line SHALL announce «Набор «X» возвращён». The trace SHALL last while the themes screen is on display and SHALL NOT be shown the next time it opens.

#### Scenario: Deleting leaves a trace in place
- **WHEN** the user deletes the collection `Solo`
- **THEN** a struck-through `Solo` with an undo mark stands where its tile stood
- **AND** focus is on «Вернуть набор «Solo»» and «Набор «Solo» удалён» is announced

#### Scenario: Nothing moves on a phone
- **WHEN** the user deletes a folder row whose name takes three lines on a phone
- **THEN** every other target on the screen stays where it was

#### Scenario: Bringing the collection back
- **WHEN** the user activates the trace of `Kurs / Kapitel 1`
- **THEN** the row `Kapitel 1` is back with its word count and has focus
- **AND** the collection's document holds its name and words again

#### Scenario: The trace is gone on the next visit
- **WHEN** the user deletes a collection, leaves the themes screen and opens it again
- **THEN** no trace is shown
