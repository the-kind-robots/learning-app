# Tasks

## 1. History

- [x] 1.1 `ports.navigation`: pure `move`, the port's `:navigation/navigate` writing history and notifying reitit; `:navigation/replace` goes.
- [x] 1.2 `main`: home entry beneath a direct landing; `:ignore-anchor-click?` skips a click already taken.
- [x] 1.3 `:effect/end-lesson` goes home through `:effect/navigate`.
- [x] 1.4 Unit test of `move` over every row of the design's table.

## 2. Screens

- [x] 2.1 Shell presenter `:corner`; the ✕ on every page but home, dispatching `:action/close-screen`; word mark and grid icon take their click.
- [x] 2.2 Words view: no back button, heading visually hidden.
- [x] 2.3 Lesson view: `.lesson__cancel` removed, progress bar spans the header.
- [x] 2.4 Presenter test for `:corner`.

## 3. Verification

- [x] 3.1 Playwright spec: ✕ per screen, words screen shape, Back after ✕, Back from a screen, direct landing, lesson from words.
- [x] 3.2 Node tests, browser suite, zprint.
- [x] 3.3 Screenshots desktop and 390 px of the words screen and the lesson.
