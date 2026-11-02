## Why

The formula that picks a lesson's words had never been checked against data. Replayed on the learner's own log (#513), it predicts recall worse than a constant (log-loss 1.756 against 0.691); FSRS-34 of #692 predicts 0.674. And a word added is written a review «retained» at once, which is no answer: it makes a new word look freshly learned.

## Changes

- The lesson and the word list read recall from FSRS-34, rated from each answer and its time to answer.
- A lesson takes one new word; each place has a small chance of going to a random known word; the rest go to the words whose review adds the most to their stability.
- Adding or renaming a word writes no review.

## Capabilities

### New Capabilities
- none

### Modified Capabilities
- `lesson`: how a lesson chooses its words; no review is written outside a lesson.
- `learner-data-memory`: a word added again, or renamed, gets no review.
- `user-phrases`: a phrase added gets no review.

## Decisions

- The kernels compared and the numbers: #513, comments «Kernels compared on the learner's log» and «Speed of FSRS-34 on a lesson's tap».
- Words are ranked by what a review adds to their stability, in log — SSP-MMC one step ahead. Simulated over 90 days of the learner's vocabulary (#513), it took 20 reviews per word lasting a month; ranking by the drop of Σ −ln P(recall) over seven days took 361, by least recall 618, and SSP-MMC's own thresholds no fewer than 37. Mean recall is about two points lower than with the Σ −ln P ranking. Least recall and SSP-MMC's thresholds stuck to failed words, up to 40 lessons in a row.
- No target retention: the lesson always takes the largest gains, so a target would change nothing (#513).
- One new word a lesson, not «at most» in the sense of «maybe none»: otherwise a learner with three known words would never meet a new one. A lesson no known word is left for is filled with new words.
- The random share draws only known words, so the quota of new words holds and the log keeps answers the ranking did not choose.
- Reviews already written on add stay; FSRS-34 skips them (#692), so no migration.
