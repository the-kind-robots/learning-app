# Change: A word picked as one part of speech gets an example for that part of speech

## Why

`rauch` picked from the suggestions as an adjective got a sentence about `der Rauch`:
the client knew the part of speech but stored only the lemma, and the backend guessed
again, taking the first entry (#585).

## What Changes

- A word added from a picked suggestion keeps the suggestion's part of speech as `pos`.
- The example request carries it as the `pos` parameter.
- The backend picks the dictionary entry of that part of speech and hands it to the model.
  An example that names the word only as a lemma of another part of speech is rejected.

## Impact

- Affected specs: `specs/data-model/spec.md`, `specs/examples/spec.md`
- Affected code: `src/client/pages/home`, `src/client/use_cases`, `src/client/adapters/example_fetch.cljs`, `src/backend/examples.clj`, `src/backend/examples/dictionary.clj`
- No migration: the field is optional.
