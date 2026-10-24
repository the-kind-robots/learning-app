## MODIFIED Requirements

### Requirement: The delete mark takes the place of the count
In editing mode, and while keyboard focus is on a target or on its delete mark, the delete mark (✕) of a tile, a folder header or a folder row SHALL stand where that target's word count stands, and the count SHALL be hidden while the mark is shown. On a folder row the mark SHALL stand at the count's horizontal place and be centred on the row's height, however many lines the name takes. The mark SHALL NOT overlap the name, and the name SHALL keep the same width and the same place in its tile whether the mark is shown or not.

#### Scenario: A row with a long name in editing mode
- **WHEN** the row `Meetings und Besprechungen mit Kollegen` under the folder `Arbeit` is in editing mode on a 384 × 800 viewport
- **THEN** the ✕ stands at the count's horizontal place, centred on the row's height, the count is not shown, and the ✕ does not overlap the name

#### Scenario: Entering editing mode moves no text
- **WHEN** a tile, a folder header or a folder row enters editing mode
- **THEN** its name keeps its width and its place in the tile
