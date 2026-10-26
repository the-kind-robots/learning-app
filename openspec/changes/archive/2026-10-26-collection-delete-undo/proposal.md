## Why

The ✕ deleted a collection at once and nothing brought it back: the owner lost a whole course of chapters to one stray tap and recovered it only from the browser console (#675).

## Changes

- The ✕ asks before deleting. Spec: [collections-navigation](specs/collections-navigation/spec.md).
- A deleted collection leaves a trace in its place with «Вернуть», which writes it back with its name and words. Spec: [collections-navigation](specs/collections-navigation/spec.md).
- The gap no longer closes after a delete: the trace keeps the place. Spec: [screen-transitions](specs/screen-transitions/spec.md).

## Capabilities

- Modified: `collections-navigation` — the delete asks first; a new requirement, the trace and «Вернуть».
- Modified: `screen-transitions` — the close-the-gap requirement is removed.

## Decisions

Chosen by the owner from mockups (comment on #675):

- The question is the system `confirm`, as for deleting a word; not a second tap on the ✕, a question inside the row or a bottom sheet.
- «Вернуть» stands in the deleted target's place; not a toast that times out.
- The trace shows an undo mark the count's width, not the word «Вернуть» of the mockup: on a phone the word is wider than a count, the name rewrapped and the rows below moved. The word stays in the button's accessible name.
- The trace lives while the themes screen is on display; it is screen state, not stored, so it is gone the next time the screen opens.
- «Вернуть» writes the collection kept on screen as a new revision of the same document, so sync carries it like any edit.
