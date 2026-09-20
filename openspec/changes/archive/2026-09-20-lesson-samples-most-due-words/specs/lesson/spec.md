## MODIFIED Requirements

### Requirement: Lesson trial generation rules
The system SHALL generate trials for each word, each phrase, and each example, using denormalized prompts and answers. Phrases SHALL NOT produce example trials.

#### Scenario: Trial generation
- **WHEN** a lesson starts
- **THEN** each word produces a word trial and each example produces an example trial
- **AND** example trials start in a locked state until the matching word trial is answered correctly

#### Scenario: Phrase trial generation
- **WHEN** a lesson starts with a phrase among the selected items
- **THEN** the phrase produces a single phrase trial with the translation as prompt and the phrase text as answer
- **AND** no example trial is generated for the phrase

## ADDED Requirements

### Requirement: A lesson samples the most due words
Words and phrases SHALL be selected into the lesson from one shared pool of the most due vocabulary in the active scope, drawn at random.

How due an item is SHALL be measured so that the measure keeps separating items however long they have gone unreviewed: the elapsed time since the last review, counted in that item's forgetting time-constants. An item never reviewed SHALL be the most due there is.

The pool SHALL hold the most due items up to a fixed size, 20 by default. The lesson's items SHALL be drawn from that pool uniformly at random, without repeats. A vocabulary smaller than the pool SHALL be the pool; a pool smaller than the lesson SHALL be taken whole.

#### Scenario: Two lessons over the same vocabulary differ
- **WHEN** lessons start repeatedly over a vocabulary larger than one lesson, none of it reviewed in between
- **THEN** they do not all serve the same items

#### Scenario: Long-unreviewed items still rank against each other
- **WHEN** two items were last reviewed five days and thirty days ago
- **THEN** the item last reviewed thirty days ago is the more due of the two

#### Scenario: Pool holds only the most due
- **WHEN** the vocabulary holds more items than the pool size
- **THEN** every item a lesson serves is among the most due up to the pool size
