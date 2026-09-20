# Change: The lesson pool breaks urgency ties at random

## Why

`lesson-samples-most-due-words` said the pool holds "the most due items up to a
fixed size" and said nothing about what happens when more items tie for the last
place in it. That silence is the remaining hole.

The alphabet never came from the retention underflow itself. It came from ties
in the sort key being resolved by the repository's read order, which is `_id`,
`"vocab:" + normalized value`. Urgency widened the key so the underflow class
stopped tying; it did not remove the fallback. Two classes still tie in bulk:

- Items with no review at all sit at the maximum together. A vocabulary
  document and its review documents are separate and can arrive apart.
- Elapsed time is truncated to whole seconds, so items added within one second
  of each other, never reviewed since, share an urgency. A batch add, an import
  or a restore produces a whole class at once.

It only bites where a subset is taken off the head of the sorted list, which is
the lesson pool. On the words page an alphabetical order among items showing the
same percentage is predictable and wanted.

## What Changes

- The vocabulary list leaves its ordering exactly as it is, and now carries the
  urgency it sorted on so a caller can break its own ties.
- The lesson pool cuts a tied class at random instead of alphabetically, while a
  strictly more due item still outranks a less due one every time.
- The whole selection policy moves into `domain.lesson`, which now receives
  every row rather than a pre-trimmed head.

## Impact

- Affected specs: `specs/lesson/spec.md`
- Affected code: `src/client/domain/lesson.cljs`,
  `src/client/use_cases/vocabulary.cljs`, `src/client/use_cases/lesson.cljs`
- Affected tests: `test/client/domain/lesson_test.cljs`,
  `test/client/domain/retention_test.cljs`, `test/client/lesson_test.cljs`
- No extra reads: the list already computed and sorted every row, and the limit
  only trimmed afterwards.
