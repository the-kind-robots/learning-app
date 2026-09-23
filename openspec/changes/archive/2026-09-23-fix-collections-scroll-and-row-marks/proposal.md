## Why

Three defects on the themes screen, seen on a phone (#456). A swipe that starts on a tile often activates that tile: the tap recovery from #404 decides at `pointercancel`, and Chrome sends that as soon as it hands the touch to the scroller — before the page has scrolled and often before any `pointermove`. In editing mode the ✕ on a folder row sits over the row's text and covers a long name. Long names break at an arbitrary letter without a hyphen (`Unterkunftsmö / glichkeiten`).

## What Changes

- A cancelled touch on a card is judged when the finger lifts, not when the browser cancels it: the finger's whole travel and the page's scroll up to that moment decide whether it was a tap. A long press gives up on movement seen after the cancel too.
- In editing mode the ✕ takes the place of the count; the name keeps its width.
- User collection names are marked as German and hyphenated at syllables; a string with no hyphenation point still cannot overflow its tile.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-navigation`: the tap recovery requirement is judged at lift instead of at cancel; new requirements for where the delete mark sits and how a long name wraps.

## Impact

- `src/client/pages/collections/effects.cljs` — gesture tracking follows touch events after `pointercancel`.
- `src/client/pages/collections/presenter.cljs`, `view.cljs` — a language on user names.
- `resources/public/css/blocks/collections.css` — ✕ placement, count hidden while editing, hyphenation.
- Tests: `test/client/pages/collections_tap_test.cljs`, a new mobile browser spec.
