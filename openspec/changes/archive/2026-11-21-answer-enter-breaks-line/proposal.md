## Why

Since #831 a plain Enter in the answer field checks nothing, but it did nothing else either: on a phone the keyboard's key still read «Done», and pressing it did nothing (#832).

## Changes

- Enter in the answer field breaks the line, as in any text field, on a phone as on a computer; the phone's keyboard shows its ordinary Enter key. `Ctrl`+`Enter` and «ПРОВЕРИТЬ» still check.

## Capabilities

### New Capabilities

### Modified Capabilities
- `lesson`: a plain Enter in the answer field breaks the line — [delta](specs/lesson/spec.md).

## Decisions

- A line break, on the phone and the computer alike (the owner's pick, #832) — over a key that does nothing, or one that hides the keyboard.
- The field's own Enter handling goes, and with it `enterkeyhint`: the textarea does what a textarea does. Grading already reads a line break as a space.
