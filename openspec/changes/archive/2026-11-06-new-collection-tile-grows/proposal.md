## Why

A deleted collection's tile shrinks away while its neighbours close the gap (#637); a new one just appears. The owner chose the reverse in the design canvas of #634.

## Changes

- Creating a collection on a phone grows its tile in its place while the tiles after it glide apart.
- Desktop width, reduced motion and a browser without the View Transition API show it at once, as for every screen change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `screen-transitions`: a new collection opens a gap.

## Decisions

- A view transition around the write and the render, as the deletion runs (ADR-0024): the neighbours move, which plain CSS on the new tile cannot show.
- The rest of #634's second part is dropped by the owner: the answer panel's rise (superseded by #747), the wrong answer's shake, the word dialog's rise and the new word's highlight.
