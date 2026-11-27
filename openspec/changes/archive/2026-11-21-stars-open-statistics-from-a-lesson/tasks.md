## 1. Test first

- [x] 1.1 The statistics spec: stars and `Alt`+`S` in a lesson, closing returns to the same trial with the typed answer; the word list keeps its search under the statistics; the phone transitions spec: deeper and back over a lesson; regenerate `test/BEHAVIOR.md`

## 2. The stack on the page (honors ADR-0029)

- [x] 2.1 The shell draws an empty `.app-shell__window` for every open window, keyed by its page, all hidden but the top; `render!` draws only the top window's screen into its own, then the settings into theirs, and unmounts a window taken off
- [x] 2.2 `open-page` opens its page anew; closing and the browser's steps show the top window as it was left, opening it when its element has no drawing (`:effect/show-window`); then the stack is saved
- [x] 2.3 The top of the stack names the screen on display; `:page/current` removed from the pages' actions and readers
- [x] 2.4 `vocabulary.css` keys on the window shown; `screen-change` plays `:in` and `:out` only for opening and closing

## 3. Stars in a lesson

- [x] 3.1 `stars-button?` and the lesson's hotkeys: the stars and `Alt`+`S` in a lesson

## 4. Verification

- [x] 4.1 The desktop, mobile and release suites pass; at 390 px the lesson keeps its strip, field and tiles under the statistics, the word list its search
- [x] 4.2 The lesson's render costs the same whatever lies beneath it (ADR-0029, the table)
