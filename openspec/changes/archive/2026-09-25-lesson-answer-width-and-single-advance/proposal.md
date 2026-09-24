## Why

Hinted words in the revealed answer are wider than the same words as plain
text, so the reference sentence does not line up with the typed one (#409).
Enter on the continue button advances the lesson twice and the second save
fails with a document update conflict (#277).

## What Changes

- Hinted answer words take the width of plain text; hover and focus keep
  their highlight without adding width.
- The continue button relies on its native Enter activation; the extra
  keydown handler that clicked it a second time is removed.
- A double click is two real clicks; an advance started while another is
  still saving joins it instead of racing it.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `lesson`: revealed answer width; continuing to the next trial.

## Impact

- **Client**: `resources/public/css/blocks/lesson.css`,
  `src/client/pages/lesson/view.cljs`, `src/client/use_cases/lesson.cljs`,
  `src/client/application.cljs` (the now unused `:action/click-if-enter`).
- **Tests**: `test/browser/lesson-answer-hints.spec.js`, new
  `lesson-advance.spec.js`, `lesson-answer.mobile.spec.js`,
  `lesson-answer.shared.js`; `test/client/lesson_test.cljs`.
