# Change: The suggestion list keeps its rows while typing

## Why

A keystroke dropped every row that no longer completed the field (#536), while the
dictionary answers with ten rows per prefix. Typing on shrank the list from ten
to one, or emptied it, until the next answer; on a phone the empty list also
animated its collapse (#584).

## What Changes

- A keystroke keeps the whole list on screen until the next answer.
- Rows that no longer complete the field stay but are inert: dimmed, not
  tappable, not picked by Enter or Tab, not counted for add-word or mode.
- The marked entry is the first row that still completes the field; with none,
  Enter moves on to the translation field.

## Impact

- Affected specs: `specs/home-add-form-focus/spec.md`
- Affected code: `src/client/pages/home/{actions,presenter,view}.cljs`,
  `src/client/domain/vocabulary.cljs`, `resources/public/css/components/autocomplete.css`
- Affected tests: `test/browser/stale-suggestions.spec.js`, `test/fixtures/dictionary/lemmas.edn`
