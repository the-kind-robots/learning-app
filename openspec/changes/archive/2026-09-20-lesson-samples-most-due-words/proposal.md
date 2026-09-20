# Change: A lesson samples the most due words

## Why

Lessons serve the same handful of words starting with `ab-`, lesson after
lesson.

Retention is `100 * exp(-rate * elapsed)`, with an initial half-life of five
minutes. A word unreviewed for 3.8 days underflows to exactly `0.0` in double
precision, so every long-unreviewed word ties at zero. `sort-by` is stable, the
ties keep the repository's read order, and that order is the document id —
`"vocab:" + normalized value`, which is the alphabet. The lesson then takes the
first rows of that list, so it walks the alphabet and re-serves the same words
every time.

## What Changes

- Order vocabulary by an urgency that does not saturate: `rate * elapsed`, the
  number of forgetting time-constants since the last review. It ranks words
  exactly as retention does, being retention's strictly decreasing image, and
  stays finite where retention underflows.
- The lesson draws a pool of the most due words and picks its words from that
  pool at random, instead of taking the top of the list.

## Impact

- Affected specs: `specs/lesson/spec.md`
- Affected code: `src/client/domain/retention.cljs`,
  `src/client/domain/lesson.cljs`, `src/client/use_cases/vocabulary.cljs`,
  `src/client/use_cases/lesson.cljs`
- Affected tests: `test/client/domain/retention_test.cljs`,
  `test/client/domain/lesson_test.cljs`, `test/client/lesson_test.cljs`
- The words page keeps showing the retention percentage it shows today; only
  the sort key changes.
