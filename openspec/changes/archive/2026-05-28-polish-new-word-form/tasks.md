## 1. Enter selects highlighted suggestion

- [x] 1.1 Add an Enter branch to `:action/handler-word-keydown` that, when suggestions are visible and an active item exists, prevents default and applies `select-item` for that item
- [x] 1.2 Verify in the browser: typing a prefix, ArrowDown to a suggestion, then Enter, fills the input with the lemma and focuses the translation field (no spurious form submit)

## 2. Blur dismisses suggestions

- [x] 2.1 Add a `:blur` handler on `#new-word-value` that clears `:home/suggestions`
- [x] 2.2 Make sure clicks on suggestion items still pick the item — use a small dispatch delay or wire dismissal through a `pointerdown` on the suggestion list before the blur, so the click-handler runs first
- [x] 2.3 Verify in the browser: open dropdown, click outside → dropdown closes; open dropdown, click a suggestion → suggestion is picked (no race)

## 3. Narrower dictionary surface-form index

- [x] 3.1 In `dictionary.kaikki/inflected-forms`, drop forms whose `tags` (or `raw_tags`) include `feminine`, `masculine`, or `abbreviation` — these are cross-references, not inflections
- [x] 3.2 Add a test in `tools/dictionary/test/dictionary/kaikki_test.clj` covering: input with a `feminine` cross-reference form is excluded from the result; normal inflections still pass
- [x] 3.3 Run `clojure -M:test` in `tools/dictionary` — all tests green
- [x] 3.4 Regenerate `dictionary.sqlite` via `clojure -X:dictionary build` from the repo root
- [x] 3.5 Manual SQLite check: `SELECT … FROM surface_forms WHERE normalized_form='mutter'` returns only "die Mutter" (and any genuine "Mutter*" lemmas), not "der Vater" / "der Vati"
- [x] 3.6 Browser verify: typing "mutter" in the form returns only Mutter-related lemmas

## 4. Visible focus ring on buttons

- [x] 4.1 Add a `:focus-visible` outline style for `.big-button` (covers ДОБАВИТЬ, НАЧАТЬ УРОК) — use a 2-3px ring in a contrasting color, offset slightly
- [x] 4.2 Add a `:focus-visible` outline for `.home__words-button` (Список слов)
- [x] 4.3 Audit other interactive buttons on the home flow (collections icon, install button if re-enabled) and add `:focus-visible` where missing
- [x] 4.4 Verify in the browser: Tab through the home page form — every focusable element shows a visible ring

## 5. Verification and closeout

- [x] 5.1 `npx shadow-cljs compile app` passes with no warnings introduced by this change
- [x] 5.2 `npx shadow-cljs compile node-test && node target/node-tests.js` — all green
- [x] 5.3 OpenSpec archive applied to `openspec/changes/archive/<date>-polish-new-word-form/`
- [x] 5.4 Canonical specs updated for `home-add-form-focus` and `dictionary-storage`
