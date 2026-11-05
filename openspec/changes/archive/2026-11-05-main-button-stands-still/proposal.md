## Why

On a phone a screen change moves the whole screen, the main button at the bottom with it, though that button has the same size and place on every screen (#767). The owner asked for it to stay and only change its label or colour (#776).

## Changes

- During a screen change the main button is a view-transition group of its own that does not move, as the bar across the top is; the browser cross-fades its old and new image in place.
- Along the way, the faint line between the bar and the words list goes (a look, not a behaviour).

## Capabilities

### New Capabilities

### Modified Capabilities
- `screen-transitions`: the main button stands still — [delta](specs/screen-transitions/spec.md).

## Decisions

- The label fades, not swaps at once: owner, #776.
- The browser's own cross-fade, only shortened to 200 ms: replacing it would drop its blending, and a label that does not change would dim halfway.
