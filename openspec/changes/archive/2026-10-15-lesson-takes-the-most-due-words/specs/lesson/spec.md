## REMOVED Requirements

### Requirement: A lesson samples the most due words
**Reason**: A lesson now takes the most due words instead of drawing from a pool of them (#582).

## ADDED Requirements

### Requirement: A lesson takes the most due words
Words and phrases SHALL be selected into the lesson from the most due vocabulary in the active scope: the lesson takes the most due items there are, up to its size.

How due an item is SHALL be measured so that the measure keeps separating items however long they have gone unreviewed: the elapsed time since the last review, counted in that item's forgetting time-constants. An item never reviewed SHALL be the most due there is.

An item that is strictly more due than another SHALL be preferred to it every time. Where more items are equally due than the lesson has room for, which of them the lesson takes SHALL be decided at random, and SHALL NOT follow the order the vocabulary is stored or listed in. A vocabulary smaller than the lesson SHALL be taken whole.

#### Scenario: Two lessons over equally due vocabulary differ
- **WHEN** lessons start repeatedly over a vocabulary larger than one lesson where every item is equally due
- **THEN** they do not all serve the same items
- **AND** they are not the ones the vocabulary happens to be listed first

#### Scenario: Long-unreviewed items still rank against each other
- **WHEN** two items were last reviewed five days and thirty days ago
- **THEN** the item last reviewed thirty days ago is the more due of the two

#### Scenario: A lesson takes the most due, not a draw from them
- **WHEN** the vocabulary holds a word just added and more long-forgotten words than one lesson takes
- **THEN** every lesson serves the long-forgotten words and never the new one

#### Scenario: A more due item beats a tied field
- **WHEN** one item is strictly more due than a field of equally due items larger than the lesson
- **THEN** the more due item is in the lesson every time
