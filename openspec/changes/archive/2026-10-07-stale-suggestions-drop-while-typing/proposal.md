## Why

The suggestion list is kept while the user types, so it does not flash empty on every keystroke (#178), and the next answer replaces it. A fast burst past the word the list answered leaves that list on screen and tappable until the answer arrives: typing «Rückenkurse» after «Rücken» on a phone and touching the kept list put «der Rücken» into the word field (#535). The spec also says Enter without an active suggestion submits the form, while the code moves focus to the translation field.

## What Changes

- On each keystroke the shown list drops, without a query, every row whose lemma no longer starts with what the field holds, compared as the query compares; rows that still match stay, and a fresh answer replaces the list as before. A dropped row can no longer be tapped or picked with Enter or Tab.
- The spec says what the code does: with no suggestion list on screen, Enter on the German word input moves focus to the translation field and does not submit the form.

What each of these must do is stated in the delta spec, not here.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `home-add-form-focus`: the kept list narrows with the typed text; the Enter requirement is restated (removed and re-added under a name covering both halves, since a scenario cannot be renamed in place) so that Enter without a list moves to the translation field.

## Impact

- `pages.home.actions` (`:action/update-word`), `domain.vocabulary`.
- Node tests in `test/client/pages/home_test.cljs`; a browser spec and one more fixture lemma (`der Rücken`) in `test/fixtures/dictionary/lemmas.edn`.
