## Why

The new-word form on the home page has four user-visible defects that erode the perceived quality of vocabulary entry:

1. **Enter on the dropdown does nothing useful.** The autocomplete supports arrow keys, Tab, and Escape but not Enter. Pressing Enter while a suggestion is highlighted submits the form with the raw text instead of the selected lemma — a UX surprise.
2. **Dropdown stays open after blur.** With no `blur` handler on the input, the suggestions list lingers after the user clicks/tabs away.
3. **Dictionary suggestions leak unrelated lemmas.** Typing "mutter" returns "der Vater" because the dictionary `surface_forms` table indexes every value found in a lemma's `:forms` array — including cross-reference forms tagged `feminine` / `masculine` / `abbreviation` (e.g. "Vater"'s feminine cross-reference "Mutter" gets indexed under "Vater"). The materializer needs to drop these non-inflectional entries before they reach `surface_forms`.
4. **No visible focus on buttons.** Keyboard users tabbing through the form's buttons (Список слов, ДОБАВИТЬ, Урок) see no focus indicator — a quiet accessibility regression that also makes keyboard-driven flows confusing.

## What Changes

- Add an Enter handler to `:action/handler-word-keydown` that picks the highlighted suggestion (parallel to the existing Tab handler) and prevents the default form submit.
- Add a `:blur` handler to the German word input that clears suggestions (using a short delay or pointer-based dismissal so clicks on suggestion items still register).
- In `dictionary.kaikki/inflected-forms`, filter out forms whose tags include `feminine`, `masculine`, or `abbreviation` — these are cross-references, not true inflections, and they pollute the `surface_forms` prefix index.
- Add a visible `:focus-visible` ring style for `.big-button`, `.home__words-button`, and other interactive buttons in the home flow.
- Rebuild `dictionary.sqlite` so the deployed surface-form index reflects the new filter.

## Impact

- `src/client/pages/home/actions.cljs` — Enter handler in `handle-word-keydown`
- `src/client/pages/home/view.cljs` — `:blur` handler on the word input
- `src/client/pages/home/effects.cljs` — possibly a small `:effect/dismiss-suggestions` effect for blur with delay
- `tools/dictionary/dictionary/kaikki.clj` — filter cross-reference forms in `inflected-forms`
- `tools/dictionary/test/dictionary/kaikki_test.clj` — new test if missing, otherwise update existing
- `resources/dictionary/dictionary.sqlite` — regenerated artifact
- `resources/public/css/blocks/big-button.css` (or similar) — `:focus-visible` outline
- `resources/public/css/blocks/home.css` — `:focus-visible` for `.home__words-button`
