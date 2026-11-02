## REMOVED Requirements

### Requirement: A lesson takes the most due words
**Reason**: Ranking by least recall sticks to failed words and spends the most reviews per word learnt (#513).
**Migration**: Replaced by «A lesson takes the words a review strengthens most».

## ADDED Requirements

### Requirement: A lesson takes the words a review strengthens most
Words and phrases SHALL be selected into the lesson from the active scope. How likely the learner recalls an item SHALL be FSRS-34's probability (#692), from the item's answers, each rated from whether it was right and how long it took against the learner's own pace. An item with no answer is new; a review an earlier version wrote when the item was added is no answer.

What a review of a known item gains SHALL be how much it is expected to add to the item's stability, in log: right with the item's probability of recall and rated Good, otherwise rated Again. The lesson aims at learning each item in the fewest reviews (#513).

When the scope holds a new item, one place of the lesson SHALL go to a new item drawn at random. Each other place SHALL go, with probability 0.1, to a known item drawn at random from those not yet in the lesson, and otherwise to the known item not yet in it whose review gains the most. A place no known item is left for SHALL go to a new item drawn at random. A vocabulary smaller than the lesson SHALL be taken whole.

Where items gain equally, which of them the lesson takes SHALL be decided at random, and SHALL NOT follow the order the vocabulary is stored or listed in.

#### Scenario: One new word a lesson
- **WHEN** the scope holds three new words and three known words, and a lesson of three starts
- **THEN** it serves exactly one new word

#### Scenario: A beginner's lesson is full
- **WHEN** the scope holds only new words, more than one lesson takes
- **THEN** the lesson serves as many words as it has places

#### Scenario: The largest gains come first
- **WHEN** the scope holds known words only, more than one lesson takes, and no place of the lesson draws a random word
- **THEN** the lesson serves the words whose review gains the most

#### Scenario: Some places go to a random word
- **WHEN** lessons start repeatedly over known words, many more than one lesson takes
- **THEN** some lessons serve a word that is not among those whose review gains the most

#### Scenario: Two lessons over equally gaining vocabulary differ
- **WHEN** lessons start repeatedly over a vocabulary larger than one lesson where every item gains equally
- **THEN** they do not all serve the same items
- **AND** they are not the ones the vocabulary happens to be listed first

## MODIFIED Requirements

### Requirement: A graded answer records its time
A review written for a graded word or phrase trial SHALL carry `answer-ms`: the time from the trial appearing on screen with its answer field to the answer being sent, on a monotonic clock, rounded to whole milliseconds. Wrong and blank answers SHALL record it too. When the page was hidden at any moment between the two, the review SHALL carry no `answer-ms`. No review SHALL be written outside a lesson: a review records an answer.

#### Scenario: The time of an answer is recorded
- **WHEN** a learner answers a word trial a few seconds after it appears
- **THEN** its review carries `answer-ms` of at least that time

#### Scenario: A wrong answer is timed too
- **WHEN** a learner sends a blank answer to a word trial
- **THEN** its review carries `retained` false and an `answer-ms`

#### Scenario: A hidden page voids the time
- **WHEN** the page is hidden and shown again while a trial is on screen, and the learner then answers it
- **THEN** its review carries no `answer-ms`

#### Scenario: Each trial is timed from its own appearance
- **WHEN** a learner answers one trial, goes on, and answers the next
- **THEN** the second review's `answer-ms` counts from the second trial appearing, not from the first
