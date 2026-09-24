## Why

A collection on the themes screen could be deleted only by a long press: the
✕ was out of the keyboard's reach and hidden from screen readers, and once a
long press revealed it, Tab met the ✕ before the theme it belongs to (#468).

## What Changes

- The ✕ follows its target in keyboard order and is always reachable; it shows
  while keyboard focus is on its target or on itself. Pointer and touch keep
  the long press.
- After a delete, focus moves to the neighbouring target, and a status message
  announces the deletion.
- The active collection's target is marked current for assistive technology.
- Keyboard focus is drawn on every target and on the ✕.
- Out of scope: undoing a delete (#470).

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `collections-navigation`: the keyboard order and reach of the delete control,
  focus after a delete, the deletion announcement, the current marker and the
  focus indicator.

## Impact

- **Client**: `src/client/pages/collections/{view,presenter,actions,effects}.cljs`,
  `resources/public/css/blocks/collections.css`.
- **Tests**: `test/browser/collections-tiles.spec.js`,
  `test/client/pages/collections_{presenter,actions,tap}_test.cljs`.
