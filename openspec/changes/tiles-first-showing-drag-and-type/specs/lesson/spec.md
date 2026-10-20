## MODIFIED Requirements

### Requirement: An example shown again offers a tile hint

Every showing of an example, the first included, SHALL show a light bulb button in the top-right corner of the answer field, over the plain field. Activating it SHALL replace the field with a tray and a bank of the answer's words as shuffled tiles, and remove the bulb; the showing SHALL NOT return to the plain field. Tiles SHALL keep the sentence's case and carry no punctuation; a repeated word SHALL be a separate tile. Word and phrase trials SHALL have no light bulb.

#### Scenario: First showing

- **WHEN** an example is shown for the first time in the lesson
- **THEN** the light bulb appears in the answer field's top-right corner
- **AND** the plain field is shown, with no tiles

#### Scenario: Shown again after a wrong answer

- **WHEN** the learner answered an example wrongly and it is shown again
- **THEN** the light bulb appears in the answer field's top-right corner

#### Scenario: Bulb activated

- **WHEN** the learner activates the light bulb
- **THEN** the field is replaced by an empty tray and a bank holding every word of the answer as a tile
- **AND** the light bulb is gone

### Requirement: Tiles are placed, returned and reordered

Activating a bank tile SHALL append it to the tray; activating a placed tile SHALL return it to the bank, and the bank SHALL close ranks with no empty places. A tile SHALL be draggable: it follows the pointer, a caret marks where it will land, and releasing it there puts it there, reordering the tray for a placed tile and inserting it into the tray for a bank tile. A bank tile released outside the tray SHALL stay in the bank.

#### Scenario: Placed tile returned

- **WHEN** the learner activates a placed tile
- **THEN** it leaves the tray and is back in the bank

#### Scenario: Placed tile dragged

- **WHEN** the learner drags a placed tile to another place in the tray and releases it
- **THEN** the tray's order changes to put it there

#### Scenario: Bank tile dragged into the tray

- **WHEN** the learner drags a bank tile into the tray and releases it at a caret between placed tiles
- **THEN** the tile is in the tray at the caret's place and has left the bank

#### Scenario: Bank tile released outside the tray

- **WHEN** the learner drags a bank tile and releases it outside the tray
- **THEN** the tile stays in the bank and the tray is unchanged

## ADDED Requirements

### Requirement: Tiles are placed by typing

While tiles are shown, letters typed on a physical keyboard SHALL accumulate as a prefix, compared without regard to case, and the pending prefix SHALL be visible near the tray. As soon as the prefix fits exactly one distinct word among the bank's tiles, one tile of that word SHALL move to the end of the tray and the prefix SHALL clear; tiles of the same text count as one word. When the prefix equals a whole bank word that is also the start of another bank word, Space or Enter SHALL move a tile of the exact word the same way. A letter that would make the prefix fit no bank word SHALL be ignored. Backspace SHALL erase the prefix's last letter, or with no prefix return the last placed tile to the bank. Typing SHALL NOT open an on-screen keyboard, and Enter with no prefix SHALL NOT place or check anything.

#### Scenario: A unique prefix places its tile

- **WHEN** the bank holds «Der», «Hund», «schläft» and the learner types «h»
- **THEN** «Hund» moves to the end of the tray and no prefix is pending

#### Scenario: An ambiguous prefix waits

- **WHEN** the bank holds «die» and «dieser» and the learner types «d», «i», «e»
- **THEN** the prefix «die» is shown as pending and nothing is placed
- **AND** pressing Space moves «die» to the end of the tray

#### Scenario: A prefix fitting no word is ignored

- **WHEN** the learner types a letter that starts no bank word
- **THEN** the letter is not accepted and the prefix is unchanged

#### Scenario: Backspace

- **WHEN** a prefix is pending and the learner presses Backspace
- **THEN** its last letter is erased
- **AND** with no prefix pending, Backspace returns the last placed tile to the bank
