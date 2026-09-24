## MODIFIED Requirements

### Requirement: Every target on the themes screen is reachable by keyboard
Every tap target on the themes screen — a collection tile, a folder header standing for a collection, and a folder row — SHALL be reachable by keyboard, in the same order the screen reads, and each named collection's delete control SHALL come right after its own target. The reached target SHALL open its collection when activated from the keyboard, exactly as a tap opens it. A folder label standing for no collection of its own is no target and SHALL be skipped. Keyboard focus SHALL be drawn visibly on every target and on every delete control.

#### Scenario: Keyboard walks the reading order
- **WHEN** «Всё подряд», the folder `Grammatik` with its one row `Konnektoren`, the folder `Kurs` with the rows `Kapitel 1` and `Kapitel 2`, and the collection `Solo` are on the themes screen
- **AND** the user moves focus forward from the top of the screen
- **THEN** focus reaches «Всё подряд», `Konnektoren`, its delete control, the header `Kurs`, its delete control, `Kapitel 1`, its delete control, `Kapitel 2`, its delete control, `Solo` and its delete control, in that order
- **AND** the label `Grammatik` is never focused

#### Scenario: Opening the focused collection
- **WHEN** focus is on the tile `Solo`
- **AND** the user activates it from the keyboard
- **THEN** `Solo` becomes the active collection and the app returns home

#### Scenario: Focus is drawn
- **WHEN** keyboard focus is on a target or on a delete control
- **THEN** a focus ring is drawn around it

### Requirement: The delete mark takes the place of the count
In editing mode, and while keyboard focus is on a target or on its delete mark, the delete mark (✕) of a tile, a folder header or a folder row SHALL stand where that target's word count stands, and the count SHALL be hidden while the mark is shown. The mark SHALL NOT overlap the name, and the name SHALL keep the same width and the same place in its tile whether the mark is shown or not.

#### Scenario: A row with a long name in editing mode
- **WHEN** the row `Meetings und Besprechungen mit Kollegen` under the folder `Arbeit` is in editing mode on a 384 × 800 viewport
- **THEN** the ✕ stands where the row's count was, the count is not shown, and the ✕ does not overlap the name

#### Scenario: Entering editing mode moves no text
- **WHEN** a tile, a folder header or a folder row enters editing mode
- **THEN** its name keeps its width and its place in the tile

## ADDED Requirements

### Requirement: The delete control is reached from the keyboard
A named collection's delete control SHALL be exposed to assistive technology and reachable by keyboard at all times, and SHALL be shown while keyboard focus is on its target or on itself; otherwise it stays hidden until a long press, and a hidden control takes no pointer tap. Activating it from the keyboard SHALL delete the collection as a tap on it does. After a delete, focus SHALL move to the target that followed the deleted one in keyboard order or, when none followed, to the one before it — a folder header left with no collection of its own is no target, so its first row takes the focus. A status message «Набор «X» удалён» SHALL announce the deletion of the collection shown as X.

#### Scenario: Deleting from the keyboard
- **WHEN** focus is on the tile `Solo`, last on the screen, and the user moves focus forward
- **THEN** focus is on the delete control labelled «Удалить набор «Solo»» and the control is shown
- **AND** activating it from the keyboard deletes `Solo`
- **AND** focus moves to the target before it, and the status message reads «Набор «Solo» удалён»

#### Scenario: Deleting a folder's parent from the keyboard
- **WHEN** the collections `Kurs`, `Kurs / Kapitel 1` and `Kurs / Kapitel 2` exist and the user deletes `Kurs` from the keyboard
- **THEN** the header `Kurs` becomes a label and focus is on the row `Kapitel 1`

#### Scenario: A hidden control takes no tap
- **WHEN** a tile is neither in editing mode nor holds keyboard focus
- **THEN** its delete control is not shown and a tap where it stands reaches the tile

### Requirement: The active collection is marked current
The target of the active collection — «Всё подряд» when no collection is active — SHALL be exposed to assistive technology as the current one, and no other target SHALL be.

#### Scenario: The active tile is current
- **WHEN** `Solo` is the active collection and the themes screen is shown
- **THEN** the tile `Solo` is marked current and «Всё подряд» is not
