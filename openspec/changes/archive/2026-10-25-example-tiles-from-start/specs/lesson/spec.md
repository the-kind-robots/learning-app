## ADDED Requirements

### Requirement: An example is answered with tiles

Every showing of an example SHALL show, in place of the plain field, a tray and a bank of the answer's words as shuffled tiles, with no light bulb; the showing SHALL NOT offer the plain field. Tiles SHALL keep the sentence's case and carry no punctuation; a repeated word SHALL be a separate tile. Word and phrase trials SHALL show the plain field and no light bulb.

#### Scenario: Example shown

- **WHEN** an example is shown in the lesson
- **THEN** the answer field is an empty tray and a bank holding every word of the answer as a tile
- **AND** there is no light bulb and no plain field

#### Scenario: Word shown

- **WHEN** a word or a phrase is to be translated
- **THEN** the plain field is shown, with no tiles and no light bulb

## REMOVED Requirements

### Requirement: An example shown again offers a tile hint

**Reason**: Tiles are shown from the start; the bulb that opened them is gone from the lesson.
**Migration**: None; see «An example is answered with tiles».

### Requirement: The light bulb answers the keyboard

**Reason**: The lesson shows no light bulb.
**Migration**: None.

### Requirement: A long example stays whole while the keyboard is open

**Reason**: An example is answered with tiles, which open no on-screen keyboard.
**Migration**: None; «Tiles are placed by typing» forbids the keyboard, and the tiles layout keeps a long example whole.

## MODIFIED Requirements

### Requirement: Tiles are placed by typing

While tiles are shown, letters typed on a physical keyboard SHALL accumulate as a prefix, compared without regard to case and as if the words had no umlauts: ä, ö, ü SHALL match a, o, u, and ß SHALL match ss. While a prefix is pending, every bank tile whose word starts with it SHALL show those leading letters in a heavier weight, and no tile SHALL change its size; the highlight SHALL clear with the prefix. As soon as the prefix fits exactly one distinct word among the bank's tiles, the first tile of that word in bank order SHALL move to the end of the tray and the prefix SHALL clear; tiles of the same text count as one word. Space and Enter SHALL act alike: when the pending prefix identifies a bank word, either as the only distinct word it fits or as a whole bank word it spells (such as «die» beside «dieser»), the first tile of that word in bank order SHALL move to the end of the tray; when it identifies none, they SHALL do nothing and the prefix SHALL stay. A letter that would make the prefix fit no bank word SHALL be ignored. Backspace SHALL erase the prefix's last letter, or with no prefix return the last placed tile to the bank. Typing SHALL NOT open an on-screen keyboard, and Enter with no prefix SHALL NOT place or check anything.

#### Scenario: A unique prefix places its tile

- **WHEN** the bank holds «Der», «Hund», «schläft» and the learner types «h»
- **THEN** «Hund» moves to the end of the tray and no prefix is pending

#### Scenario: Umlauts typed without them

- **WHEN** the bank holds «über» and «groß» beside words that start otherwise
- **THEN** typing «u» places «über»
- **AND** typing «g», «r», «o», «s», «s» places «groß»

#### Scenario: A pending prefix is highlighted on the tiles

- **WHEN** the bank holds «die» and «dieser» and the learner types «d», «i»
- **THEN** the letters «di» are heavier on both tiles and the tiles keep their widths
- **AND** the highlight is gone once the prefix is cleared or the tile is placed

#### Scenario: A whole word that starts another waits for Space or Enter

- **WHEN** the bank holds «die» and «dieser» and the learner types «d», «i», «e»
- **THEN** nothing is placed and «die» stays highlighted
- **AND** pressing Space or Enter moves «die» to the end of the tray

#### Scenario: An ambiguous prefix that is no whole word

- **WHEN** the bank holds «die» and «dieser», the prefix «di» is pending and the learner presses Space or Enter
- **THEN** nothing is placed and the prefix stays

#### Scenario: Duplicate words

- **WHEN** the bank holds two tiles «der» and the learner types «d», «e», «r»
- **THEN** the first «der» in bank order moves to the tray and the other stays in the bank

#### Scenario: A prefix fitting no word is ignored

- **WHEN** the learner types a letter that starts no bank word
- **THEN** the letter is not accepted and the prefix is unchanged

#### Scenario: Backspace

- **WHEN** a prefix is pending and the learner presses Backspace
- **THEN** its last letter is erased
- **AND** with no prefix pending, Backspace returns the last placed tile to the bank
