# Suggestion list stops flashing between keystrokes

## Why

GH-178 ("Also worth a look"): every keystroke dispatches `:action/update-word`, which resets `:home/suggestions` along with saving the word. The list goes blank for the 150 ms debounce plus the dictionary query on every character, so it visibly flashes while typing. Keeping the previous list until `:action/update-suggestions` delivers the next answer renders the same number of times but looks steady.

Out of scope: auto-filling the translation from the top suggestion (the other half of GH-178) is an open UX question for the owner and is deliberately not implemented here.

## What changes

- `:action/update-word` in `pages.home.actions` no longer writes `:home/suggestions`; it saves only `:home/word` and the cleared `:home/translation`.
- Every other clear survives untouched: dictionary answer replaces the list (`:action/update-suggestions`; the adapter answers `[]` for an empty prefix, so an emptied input still clears), blur dismisses (`:action/dismiss-suggestions`), Escape clears, picking an entry clears (`select-item`), and a successful submit clears via `:action/show-home`.
- A pick from a list kept across keystrokes stays coherent: `select-item` fills word and translation from the picked entry's own `:lemma`/`:translations`, never from the current input.
- Node tests cover the lifecycle in `test/client/pages/home_test.cljs`.

## Impact

Modified: `home-add-form-focus`. Files: `src/client/pages/home/actions.cljs`, `test/client/pages/home_test.cljs`. Visual steadiness check pends the shared dev stand.
