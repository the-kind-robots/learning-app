# Change: A word saved in an inflected form gets an example

## Why

The validator compared strings only, so a word saved as `explodiert` never
matched the sentence's `explodierte` nor the model's `dictionaryForm`
`explodieren`, and the endpoint answered 422 (#581). The dictionary's form index
already resolves these forms to their lemmas.

## What Changes

- A single-lemma target is also present when a sentence word or some
  `dictionaryForm` shares a dictionary lemma with it; the same per required word
  of a phrase. Old rules stay, so separable verbs and forms missing from the
  index keep passing.
- The prompt gives the model the word's lemma and says any form of it will do.

## Impact

- Affected specs: `specs/examples/spec.md`
- Affected code: `src/backend/examples.clj`, `src/backend/examples/dictionary.clj`
- Affected tests: `test/backend/examples_test.clj`, `test/backend/core_test.clj`
