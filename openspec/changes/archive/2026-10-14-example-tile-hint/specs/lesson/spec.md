## ADDED Requirements

### Requirement: An example shown again offers a tile hint

The first showing of an example SHALL offer the plain answer field with no hint. An example shown again after a wrong answer SHALL show a light bulb button in the top-right corner of the answer field. Activating it SHALL replace the field with a tray and a bank of the answer's words as shuffled tiles, and remove the bulb; the showing SHALL NOT return to the plain field. Tiles SHALL keep the sentence's case and carry no punctuation; a repeated word SHALL be a separate tile.

#### Scenario: First showing

- **WHEN** an example is shown for the first time in the lesson
- **THEN** the answer field has no light bulb

#### Scenario: Shown again after a wrong answer

- **WHEN** the learner answered an example wrongly and it is shown again
- **THEN** the light bulb appears in the answer field's top-right corner

#### Scenario: Bulb activated

- **WHEN** the learner activates the light bulb
- **THEN** the field is replaced by an empty tray and a bank holding every word of the answer as a tile
- **AND** the light bulb is gone

### Requirement: Tiles are placed, returned and reordered

Activating a bank tile SHALL append it to the tray; activating a placed tile SHALL return it to the bank, and the bank SHALL close ranks with no empty places. A placed tile SHALL be reorderable by dragging it: it follows the pointer, a caret marks where it will land, and releasing it there reorders the tray.

#### Scenario: Placed tile returned

- **WHEN** the learner activates a placed tile
- **THEN** it leaves the tray and is back in the bank

#### Scenario: Placed tile dragged

- **WHEN** the learner drags a placed tile to another place in the tray and releases it
- **THEN** the tray's order changes to put it there

### Requirement: A tile answer is checked like a typed one

The check button SHALL be available whatever the number of placed tiles. The answer SHALL be the tiles' texts in tray order, separated by spaces, and SHALL be graded exactly as a typed answer: word order is strict.

#### Scenario: Tiles left in the bank

- **WHEN** at least one tile is still in the bank and the learner checks
- **THEN** the placed tiles are graded as the answer, and shown as wrong when they are not the whole sentence
- **AND** with no tile placed the answer is graded as not recalled

#### Scenario: Tiles in the right order

- **WHEN** every tile is placed in the sentence's order and the learner checks
- **THEN** the lesson shows «Правильно!»

