# Tasks

## 1. The keystroke

- [x] 1.1 `:event.keyboard/alt?` placeholder beside the other modifiers in
      `src/client/application.cljs`
- [x] 1.2 `:action/start-lesson-if-alt-enter` in `pages.home.actions`: navigate
      on `Alt`+`Enter` with the default suppressed, nothing otherwise
- [x] 1.3 `:keydown` on `[:div.home ...]`, so the keystroke bubbles up from
      either textarea and from the screen's buttons
- [x] 1.4 The vocabulary predicate is read from state in the action, the way
      the screen's other keydown actions read theirs; the view computes nothing

## 2. Verify

- [x] 2.1 `Alt`+`Enter` opens the lesson from the word field, the translation
      field and a button — `test/browser/home-lesson-hotkey.spec.js`, real
      Chrome
- [x] 2.2 `Ctrl`+`Enter` still adds a word; `Enter` on the word field still
      moves to the translation
- [x] 2.3 With an empty vocabulary `Alt`+`Enter` leaves the screen where it is
- [x] 2.4 The spec is a net, not a tautology: with the page handler removed and
      the bundle rebuilt, the three positive cases fail and the two control
      cases pass
- [x] 2.5 Browser suite green (33 passed); node suite green (225 tests, 533
      assertions, 0 failures)
