## Why

Screen changes on a phone move since the first change of #634; what appears inside a screen still cuts in. The owner chose this motion in the same design canvas.

## Changes

- The answer panel rises from the bottom; on a wrong answer the line with the learner's answer shakes once it is up.
- The word dialog rises from the bottom while its backdrop fades in.
- Words added on home since the word list was last opened are highlighted there once.
- A new collection's tile grows in its place while the tiles after it glide apart.
- Desktop width and reduced motion keep all of it still, as screen changes do.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `screen-transitions`: motion inside a screen.

## Decisions

- What shakes on a wrong answer is the line «Ваш ответ», over the whole panel — the owner's choice: it is the part that was wrong. The answer field is gone by then, replaced by the panel.
- A word counts as new from the moment home adds it until the word list next opens, over only the last word added — the owner's choice. It is kept in page memory: a reload forgets it.
- Home keeps what it added and the opening list takes it, over the list forgetting it as it unmounts — a save from an unmount hook renders while Replicant is rendering.
- The rest of what the canvas shows on home (the «Список слов» pulse, the fields fading) is not part of #634.
- Ordering the list newest first, which the owner asked for alongside, is #656: it re-keys learner memory and replaces `vocabulary-list-paging`'s order.
- Appearing, rising, shaking and highlighting are CSS animations on the mounted element (ADR-0024); the new tile is a view transition, the reverse of the deletion, because its neighbours move.
