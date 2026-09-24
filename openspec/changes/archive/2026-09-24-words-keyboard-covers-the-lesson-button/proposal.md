## Why

With the words search in the bottom tray, the phone keyboard lifted the whole
tray, the lesson button included. The owner wants the button to stay at the
bottom, behind the keyboard, with only the search field above it.

## What Changes

- The words screen asks the browser to lay the keyboard over the page, as the
  home screen does. The lesson button stays fixed at the bottom and the
  keyboard covers it; the search field sits directly above the keyboard and
  the list gives up the height.
- A browser that cannot overlay the keyboard resizes the page instead and
  carries the whole tray up.
- Nothing changes with the keyboard closed.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `app-navigation`: what stands above the keyboard on the words screen.

## Impact

- **Client**: `src/client/pages/words/view.cljs` (overlay opt-in, the tray
  split into the search bar and the fixed footer),
  `resources/public/css/blocks/vocabulary.css`.
- **Tests**: `test/browser/words-tray.mobile.spec.js`,
  `test/browser/words-tray.shared.js`.
