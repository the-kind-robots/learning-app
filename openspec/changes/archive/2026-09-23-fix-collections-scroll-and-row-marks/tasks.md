## 1. Gesture

- [x] 1.1 Follow a cancelled touch through `touchmove`/`touchend` and decide the tap at the lift (`effects.cljs`, `on-lift`)
- [x] 1.2 Long press gives up on travel seen after the cancel
- [x] 1.3 Unit tests for `on-lift`

## 2. Layout

- [x] 2.1 ✕ in the count's place on tile, folder header and row; count hidden while editing
- [x] 2.2 `lang="de"` on collection names from the presenter; `hyphens: auto`

## 3. Verification

- [x] 3.1 Mobile browser spec: swipe with a real `pointercancel` activates nothing; still tap and cancelled still tap activate; ✕ clear of the name; hyphenation at syllables
- [x] 3.2 Existing collections specs, client unit tests
- [x] 3.3 Before/after screenshots at 384 × 800
