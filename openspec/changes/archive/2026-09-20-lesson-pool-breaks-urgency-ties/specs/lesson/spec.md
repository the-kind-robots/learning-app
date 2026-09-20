## MODIFIED Requirements

### Requirement: A lesson samples the most due words
Words and phrases SHALL be selected into the lesson from one shared pool of the most due vocabulary in the active scope, drawn at random.

How due an item is SHALL be measured so that the measure keeps separating items however long they have gone unreviewed: the elapsed time since the last review, counted in that item's forgetting time-constants. An item never reviewed SHALL be the most due there is.

The pool SHALL hold the most due items up to a fixed size, 20 by default. Where more items are equally due than the pool has room for, which of them the pool holds SHALL be decided at random, and SHALL NOT follow the order the vocabulary is stored or listed in. An item that is strictly more due than another SHALL still be preferred to it every time.

The lesson's items SHALL be drawn from that pool uniformly at random, without repeats. A vocabulary smaller than the pool SHALL be the pool; a pool smaller than the lesson SHALL be taken whole.

#### Scenario: Two lessons over the same vocabulary differ
- **WHEN** lessons start repeatedly over a vocabulary larger than one lesson, none of it reviewed in between
- **THEN** they do not all serve the same items

#### Scenario: Long-unreviewed items still rank against each other
- **WHEN** two items were last reviewed five days and thirty days ago
- **THEN** the item last reviewed thirty days ago is the more due of the two

#### Scenario: Pool holds only the most due
- **WHEN** the vocabulary holds more items than the pool size
- **THEN** every item a lesson serves is among the most due up to the pool size

#### Scenario: More items tie for the pool than it has room for
- **WHEN** lessons start repeatedly over a vocabulary where every item is equally due and there are more of them than the pool size
- **THEN** the items the pool holds are not the same ones every time
- **AND** they are not the ones the vocabulary happens to be listed first

#### Scenario: A more due item beats a tied field
- **WHEN** one item is strictly more due than a field of equally due items that fills the pool
- **THEN** the more due item is in the pool on every lesson
