## Why

«ПРОВЕРИТЬ» does nothing while the answer field is empty, so a learner who cannot recall the word has to type a meaningless placeholder, which is graded as wrong anyway (#552).

## What Changes

- Checking an empty or whitespace-only answer grades it as not recalled, like any wrong answer, for every trial type: the correct answer is revealed and, for a word or phrase trial, a review is recorded as not retained.
- A double click acts once: the second and later clicks of a series do nothing, so the second click of a double click on «ДАЛЕЕ», which lands on «ПРОВЕРИТЬ» of the next trial on a phone, grades nothing.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: the requirement that a blank answer is not checked becomes the requirement that it is graded as not recalled.
- `app-navigation`: a double click acts once on any control, not only where a screen changes.

## Impact

- `src/client/domain/lesson.cljs`, `src/client/use_cases/lesson.cljs`, their tests, and the browser spec of the blank check.
