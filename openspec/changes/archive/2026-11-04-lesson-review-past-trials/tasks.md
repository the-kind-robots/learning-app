## 1. Tests

- [x] 1.1 Browser spec `test/browser/lesson-review.spec.js` (and `.mobile.spec.js`) for the scenarios of [the delta](specs/lesson/spec.md); `npm run test:catalog`

## 2. Domain

- [x] 2.1 `domain.lesson`: the misses per trial and the past trials with their attempts, kept by `check-answer` and `advance`

## 3. Screen

- [x] 3.1 Presenter: past pages, viewed trial, progress
- [x] 3.2 View: strip of challenges, panel of the viewed past trial over the current one, desktop arrow
- [x] 3.3 Actions and effects: viewed trial from the scroll, scroll back and to the current trial
- [x] 3.4 CSS: strip with snap, panel overlay, arrow on fine pointers
- [x] 3.5 i18n: the attempts and the arrow label in every language

## 4. Verify

- [x] 4.1 Browser suite passes (desktop and mobile); the swipe on a real phone is the owner's check
