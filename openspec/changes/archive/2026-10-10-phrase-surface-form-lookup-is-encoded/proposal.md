## Why

A phrase without an article never gets an example (#542). The surface-form lookup puts the phrase into the dictionary URL unencoded, http-kit refuses a path with spaces, the lookup throws, generation fails and the endpoint answers 503. The pair stays first-missing and the pairs behind it are never asked for.

## What Changes

- The surface-form lookup encodes the document id, so a phrase is found like a word (restores the phrase behaviour of #371).

## Capabilities

### New Capabilities

### Modified Capabilities
- `examples`: the dictionary lookup requirement states that a phrase is found like a word.

## Impact

- `src/backend/examples/dictionary.clj`, `test/backend/examples_test.clj`.
