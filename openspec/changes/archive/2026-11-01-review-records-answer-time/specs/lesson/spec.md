## ADDED Requirements

### Requirement: A graded answer records its time
A review written for a graded word or phrase trial SHALL carry `answer-ms`: the time from the trial appearing on screen with its answer field to the answer being sent, on a monotonic clock, rounded to whole milliseconds. Wrong and blank answers SHALL record it too. When the page was hidden at any moment between the two, the review SHALL carry no `answer-ms`. A review written outside a lesson — the initial review of a new word — SHALL carry none.

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
