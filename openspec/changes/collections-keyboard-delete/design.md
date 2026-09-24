## Context

The ✕ is a sibling of its target (a button holds no button), laid over the
count by absolute positioning. It was rendered before the target and removed
from the Tab order and the accessibility tree outside editing mode.

## Decisions

- **Order by DOM, place by CSS.** The ✕ moves after its target in the DOM; its
  box is absolutely positioned, so nothing moves on screen.
- **Reveal on `:focus-visible` inside the box, not `:focus-within`.** A mouse
  click focuses a button too; `:has(:focus-visible)` on the tile, header or
  row shows the ✕ for keyboard focus only, so pointer behaviour is unchanged.
  Pointer events follow the same rule, so a hidden ✕ takes no tap.
- **The count goes transparent, not `visibility: hidden`.** Hidden, it left
  the target's accessible name the moment focus arrived, so a screen reader
  heard «Solo» where the tile says «Solo 1». Editing mode follows suit.
- **Neighbour from the DOM before the delete.** The effect reads the targets'
  ids in document order, picks the one after the deleted id (else the one
  before) with a pure function, and focuses it once the reloaded screen has
  rendered — a render is synchronous on every save. «Всё подряд» is never
  deletable and always first, so there is always a neighbour. A deleted
  folder parent is followed by its first row, which is the requirement.
- **The announcement is state.** The effect records the deleted name; the
  presenter turns it into the message; a live region outside `.masonry`
  (whose children must be tiles only, for the accent count) shows it. Opening
  the screen clears it.
- **Ring outside the accents.** A dark outline with an offset: around a plain
  tile it clears the tile's accent border and stands on the page background;
  on a header, row and ✕ it stands on the tile's white. The app has no dark
  theme to adapt to.

## Risks / Trade-offs

- [The ✕ is always in the Tab order] → each named collection costs two Tab
  stops. Accepted by the owner over a custom listbox/grid pattern.
