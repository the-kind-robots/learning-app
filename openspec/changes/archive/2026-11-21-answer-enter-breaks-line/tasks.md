## 1. Tests

- [x] 1.1 `lesson-submit.spec.js`: Enter after typing breaks the line and checks nothing; an answer on two lines is right by `Ctrl`+`Enter`; the end-of-lesson test no longer expects an empty field
- [x] 1.2 `npm run test:catalog`

## 2. Answer field

- [x] 2.1 `answer_field.cljs`: the textarea's own Enter handling goes; `enterkeyhint` is no longer forwarded
- [x] 2.2 `view.cljs`: no `enterkeyhint`

## 3. Verify

- [x] 3.1 The lesson specs pass locally: desktop `lesson-submit`, `lesson-review`, `lesson-tiles`, `lesson-stars`, `double-click` (62); mobile `lesson-keyboard`, `lesson-review`, `double-click`, `lesson-tiles-layout` (9). The phone's own keyboard is not checked
