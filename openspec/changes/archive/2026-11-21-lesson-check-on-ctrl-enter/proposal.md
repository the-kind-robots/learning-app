## Why

At the end of a lesson, two quick Enters started a new lesson and then checked its first word with an empty answer, counted wrong (#829): the first Enter pressed «ЕЩЁ УРОК», the second landed in the new answer field, where a plain Enter checked.

## Changes

- **BREAKING** A plain Enter in the answer field, the Done key of a phone's keyboard among it, checks nothing; `Ctrl`+`Enter` checks, and the «ПРОВЕРИТЬ» button.
- When the current trial waiting for its answer comes on display and no button holds the focus, the focus lands in the answer field, the return from a trial left behind among it.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: Enter in the answer field no longer checks; the answer field takes the focus no button holds — [delta](specs/lesson/spec.md).

## Decisions

- Only `Ctrl`+`Enter` checks (the owner's pick, #829) — over a plain Enter that checks a field only once something is typed in it.
- The focus goes to the field only with a fine pointer, as it does when the lesson opens: on a phone it would raise the keyboard.
- The Done key keeps its label and does nothing, as a plain Enter on a computer; hiding the keyboard on it is left out.
