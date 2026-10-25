## MODIFIED Requirements

### Requirement: An example is answered with tiles

Every showing of an example SHALL show, in place of the plain field, a tray and a bank of the answer's words as shuffled tiles, with no light bulb; the showing SHALL NOT offer the plain field. Tiles SHALL keep the sentence's case and carry no punctuation at a word's edges; a symbol written with the word — a currency sign, a percent sign — SHALL stay on its tile, and a symbol written apart SHALL share the tile of the word before it. A repeated word SHALL be a separate tile. Word and phrase trials SHALL show the plain field and no light bulb.

#### Scenario: Example shown

- **WHEN** an example is shown in the lesson
- **THEN** the answer field is an empty tray and a bank holding every word of the answer as a tile
- **AND** there is no light bulb and no plain field

#### Scenario: Amount with its sign

- **WHEN** an example whose answer holds «1000€» and «50%» is shown in the lesson
- **THEN** the bank holds the tiles «1000€» and «50%», and no tile ends in the sentence's full stop

#### Scenario: Amount with its sign written apart

- **WHEN** an example whose answer holds «1000 €» and «50 %» is shown in the lesson
- **THEN** the bank holds the tiles «1000 €» and «50 %», and no tile holds a sign alone

#### Scenario: Word shown

- **WHEN** a word or a phrase is to be translated
- **THEN** the plain field is shown, with no tiles and no light bulb
