## Why

A translation entered on the home add-word form is split on `[,;.]` before it is stored
(`domain/vocabulary.cljs`, `parse-translations`), so the separator lives inside the data:
`без того, чтобы` becomes two translations, and so does any aspect pair written with a dot.

The split buys nothing. Grading compares the typed answer against the German `:answer`
(`domain/lesson.cljs`, `vocab->trial`), never against the elements; display glues the vector
back together with the same separator it was cut on. The elements are read for one thing —
deduplication in `merge-translations` when the same word is added again. The string is cut on
a comma so it can be joined by a comma again, and the only observable effect is that
translations containing a comma are destroyed. Phrases already store their translation whole
(`domain/phrase.cljs`), so words are the outlier.

## What Changes

- `parse-translations` keeps the entered text as one translation entry. A blank entry still
  yields no translations, so the `:empty-translations` guard in `use-cases/vocabulary` holds.
- The translation field accepts several lines. `Ctrl`/`Cmd`+`Enter` submits; bare `Enter`
  inserts a newline. The submit button stays and remains the only path on a phone, where
  neither modifier can be typed.
- The dictionary prefill hands over the stored text rather than re-spacing it: both
  `str/join ", "` sites in `pages/home/actions.cljs` trim the pieces the transport's split
  left, so a stored `без того, чтобы` reaches the field as it is stored instead of gaining a
  second space, while several translations still read as `пёс, собака`.
- The word edit dialog's translation field becomes a `textarea` like the phrase one: an
  `input` silently swallows the line breaks a multi-line translation carries.
- A stored line break is shown as one: `white-space: pre-line` on the word list's translation
  and on the lesson prompt.
- `Enter` on the German word input is untouched — it picks the highlighted suggestion or moves
  to the translation field (`handler-word-keydown`).
- The document schema does not change: `:translation` stays a vector, normally holding one
  element, and existing documents are read as they are. `update-word` shares
  `parse-translations`, so editing follows the same rule.

Given up knowingly: deduplication on re-adding a word compares whole strings instead of
variants inside them. If a drill that accepts any one of the translations appears, or a display
that shows them one at a time, the elements have to be recovered. Neither exists today.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `data-model`: a word's `translation` entries are whole strings, never split on punctuation —
  the rule phrases already carry, now stated for every vocabulary document.
- `home-add-form-focus`: the translation field's key contract — bare `Enter` inserts a newline,
  `Ctrl`/`Cmd`+`Enter` submits, the button submits — and the prefill handing over the stored
  text unaltered.

## Impact

- `src/client/domain/vocabulary.cljs` — `parse-translations`.
- `src/client/pages/home/actions.cljs` — `:action/submit-if-enter` gives way to the shared
  `:action/submit-if-ctrl-enter`; both prefill joins.
- `src/client/application.cljs` — `:event.keyboard/meta?` placeholder;
  `:action/submit-if-ctrl-enter` accepts `Cmd` and prevents the default.
- `src/client/pages/home/view.cljs` — the translation textarea's keydown wiring.
- `src/client/pages/words/view.cljs` — the edit dialog's translation field.
- `resources/public/css/blocks/` — `word-edit-dialog.css`, `word-item.css`, `lesson.css`.
- Inherited by the edit path: `use-cases/vocabulary.cljs` `update!` for word documents.
- Tests: `test/client/domain/vocabulary_test.cljs`, `test/client/pages/home_test.cljs`.
- Not in scope: #362, the dictionary transport that does the splitting at the source.
