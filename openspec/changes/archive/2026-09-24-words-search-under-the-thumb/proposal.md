## Why

With the heading gone (#411), the search field sat right against the shell
bar, and on a phone it was the farthest control from the thumb. The owner
asked for more air under the bar and for the field at the bottom.

## What Changes

- The words search field moves into the bottom tray, directly above «Начать
  урок» and as wide as it, the way the lesson puts its answer field above its
  button.
- The rows start under the shell bar with a gap.
- The words screen follows the lesson's keyboard handling: the viewport
  resizes, and the tray stays above the keyboard.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `app-navigation`: where the words screen puts its search field.

## Impact

- **Client**: `src/client/pages/words/view.cljs` (tray markup, keyboard
  overlay opt-out), `resources/public/css/blocks/vocabulary.css`.
- **Tests**: `test/browser/close-and-back.spec.js`, new words-tray check.
