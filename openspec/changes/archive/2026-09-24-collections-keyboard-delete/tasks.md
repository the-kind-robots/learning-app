# Tasks

## 1. Keyboard reach

- [x] 1.1 View: ✕ after its target in tile, header and row; no `aria-hidden`, no `tabindex`.
- [x] 1.2 CSS: ✕ and transparent count while `:focus-visible` is inside the tile, header or row; pointer events only then or in editing.
- [x] 1.3 CSS: focus ring on targets and ✕.

## 2. After a delete

- [x] 2.1 Effect: neighbour target from the DOM, focused after the reload.
- [x] 2.2 State `:collections/deleted-name`; presenter message; `role="status"` outside `.masonry`; cleared on open.

## 3. Current

- [x] 3.1 View: `aria-current` from `:active?`.

## 4. Tests

- [x] 4.1 Unit: neighbour choice, message.
- [x] 4.2 Browser: Tab order with ✕, Enter deletes, focus on neighbour, status text, `aria-current`, folder parent deletion; a touch delete reveals no ✕.
