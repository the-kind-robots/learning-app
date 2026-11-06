## Why

On a desktop the lesson asks for the mouse where the keyboard should do (#771): `Escape` does not finish a lesson that is over, the trials answered are paged back only by a wheel, a trackpad or the arrow `‹`, and on a trial left behind `Enter` does not return.

## Changes

- `Escape` finishes a lesson that is over, as «ЗАКОНЧИТЬ» does.
- `ArrowLeft` / `ArrowRight` page the trials; the arrow `‹` beside the progress bar goes.
- On a trial left behind `Enter` and `Ctrl`+`Enter` press its main button.
- An open menu or word card takes the keys: before, `Escape` on the words screen with the settings menu open closed the screen and left the menu open; on a lesson that is over it would have done the same to the word card.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: looking back at the trials and the end of a lesson, from the keyboard — [delta](specs/lesson/spec.md).
- `app-navigation`: an open menu takes `Escape` — [delta](specs/app-navigation/spec.md).

## Decisions

- The arrow `‹` goes: a wheel or a trackpad already scrolls the strip — owner, #771.
- While the current trial waits for its answer, the arrows stay with the answer field, where they move the caret.
- Mid-lesson `Escape` still does nothing: a lesson is left by the corner ✕ only, so a stray key does not lose it.
