## 1. Behaviour first

- [x] 1.1 Browser tests in `test/browser/lesson-submit.spec.js` for the scenarios of `specs/lesson/spec.md`; `npm run test:catalog`

## 2. Clock and page

- [x] 2.1 `ports.world` clock answers `:uptime-ms`, a monotonic time (`performance.now`)
- [x] 2.2 `ports.page` counts the page's visibility changes since start

## 3. Lesson

- [x] 3.1 The first paint after the answer field mounts marks the trial shown in app state; a new lesson state clears the mark
- [x] 3.2 `check-answer!` turns the mark into `answer-ms`, none when visibility changed
- [x] 3.3 `record-review!` → `add-review` → `new-review` carry `answer-ms` into the document

## 4. Verify

- [x] 4.1 Unit tests and the lesson browser specs pass
- [x] 4.2 `smell-reviewer` on the branch diff
