## 1. Tests

- [x] 1.1 `lesson-submit.spec.js`: two Enters at the end of a lesson start a new one and check nothing (fails on `master`: its first word counted wrong); Enter after typing checks nothing; the answer-time tests check by `Ctrl`+`Enter`
- [x] 1.2 `lesson-review.spec.js`: back from a trial left behind to a word waiting for its answer, the field has the focus
- [x] 1.3 `npm run test:catalog`

## 2. Answer field

- [x] 2.1 `answer_field.cljs`: a plain Enter in the textarea is prevented and submits nothing
- [x] 2.2 `view.cljs`: the field takes the focus on its update too, so the return from a trial left behind puts it there

## 3. Verify

- [x] 3.1 The lesson specs pass locally: desktop `lesson-submit`, `lesson-review`, `lesson-tiles`, `lesson-stars`, `double-click` (61); mobile `lesson-keyboard`, `lesson-review`, `double-click` (5)
- [x] 3.2 Desktop browser: two Enters at the end of a lesson, then `Ctrl`+`Enter` checks — headless Chrome on the branch build; not yet on the stand
