# Change: A lesson takes the most due words

## Why

A lesson drew its words uniformly from the 20 most due (#431). With 20 words or
fewer that pool is the whole vocabulary, so words just added land in the next
lesson as often as words forgotten long ago. The learner wants the most
neglected words first (#582).

## What Changes

- A lesson takes the `n` most due items. The pool and its size are gone.
- Ties are still broken at random, so the alphabetical walk of #431 stays fixed.

## Impact

- Affected specs: `specs/lesson/spec.md`
- Affected code: `src/client/domain/lesson.cljs`, `src/client/use_cases/lesson.cljs`
- Affected tests: `test/client/domain/lesson_test.cljs`, `test/client/lesson_test.cljs`,
  `test/client/memory_snapshot_test.cljs`
