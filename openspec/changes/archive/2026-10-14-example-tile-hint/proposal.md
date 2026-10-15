## Why

An example the learner got wrong comes back later in the lesson, and the learner types the whole sentence again from memory (#573). A hint that sets the words as tiles lets them rebuild the sentence instead.

## What Changes

- An example shown again after a wrong answer offers a light bulb in the answer field's corner.
- The bulb swaps the field for a tray and a bank of shuffled word tiles; the tiles are put in order by tapping and dragging.

## Capabilities

### Modified Capabilities
- `lesson`: the answer field of a repeated example and the tile answer.

## Impact

- `src/client/domain/lesson.cljs`, new `src/client/domain/tiles.cljs`, `src/client/pages/lesson/*`, `resources/public/css/blocks/lesson.css`, domain and browser tests.
