## 1. Keep the entered text whole

- [x] 1.1 `domain/vocabulary.cljs`: `parse-translations` returns one entry holding the trimmed
  entered text, and nothing for blank input.
- [x] 1.2 Cover it in `test/client/domain/vocabulary_test.cljs`: a comma, a semicolon, a dot and
  a slash survive; several lines survive; blank yields no entries; `update-word` inherits the
  rule.

## 2. Keys on the translation field

- [x] 2.1 `application.cljs`: add the `:event.keyboard/meta?` placeholder and let
  `:action/submit-if-ctrl-enter` accept `Cmd` as well as `Ctrl`, suppressing the default so the
  form submits once.
- [x] 2.2 `pages/home/actions.cljs`: drop `:action/submit-if-enter`.
- [x] 2.3 `pages/home/view.cljs`: wire the translation textarea's keydown to
  `:action/submit-if-ctrl-enter` with `key`, `ctrl?` and `meta?`. Leave the German input alone.

## 3. Prefill hands over the stored text

- [x] 3.1 `pages/home/actions.cljs`: both prefill sites trim the pieces the transport's split
  left before rejoining them, so the dictionary's text arrives as it is stored.
- [x] 3.2 Add a `test/client/pages/home_test.cljs` case for a stored translation containing a
  comma.

## 3a. A line break survives the round trip

- [x] 3a.1 `pages/words/view.cljs`: the edit dialog's translation field becomes a `textarea`
  for words too, with the same `Ctrl`/`Cmd`+`Enter` submit; an `input` swallows line breaks.
- [x] 3a.2 CSS: `--multiline` carries the autogrow rules the phrase modifier used to hold, and
  `white-space: pre-line` on the word list's translation and the lesson prompt.

## 4. Verify

- [x] 4.1 `npx shadow-cljs compile node-test && node target/node-tests.js` green.
- [x] 4.2 In a real browser: `Enter` in the translation field inserts a newline,
  `Ctrl`+`Enter` submits, the button submits, and a translation with a comma is stored whole.
