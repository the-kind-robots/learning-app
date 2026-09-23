## Why

A name typed into the home heading and left there while the collections icon
is tapped reaches the themes screen only on its next visit (#460). The rename
starts on the heading's blur and awaits three reads before it writes; the
themes screen opens on the same tap and reads its collections before the write
lands. Traced in the browser suite, three runs of three: rename start, themes
screen read (old name), rename written — and no read after it.

## What Changes

- A rename that writes asks the screen on display to read its data again, the
  way a sync pull that brought documents already does. On the themes screen
  that is its collections; on home, its heading.
- A refused rename (blank, taken, unchanged) writes nothing and asks for no
  read.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `collections-navigation`: the heading rename reaches the themes screen
  opened while it is still being written.

## Impact

- **Client**: `src/client/use_cases/collections.cljs` (`rename-active!` says
  whether it wrote), `src/client/pages/home/effects.cljs` (the rename effect
  reloads the current page).
- **Tests**: `test/client/collections_test.cljs`,
  `test/browser/collections-tiles.spec.js`.
