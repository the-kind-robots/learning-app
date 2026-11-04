## Why

The dictionary lookup behind example generation keeps the entries written exactly as the target first only while a form has at most eight lemmas. With «sie» (nine lemmas share it in the built dictionary), the lookup today answers `ihnen` first and `sie` last, and a request whose gloss matches no entry is built for the lemma `ihnen` (#741).

## Changes

- `utils/distinct-by` keeps the first element for each key in the order of its input, and calls the key function once per element.
- `merge-dictionary-entry-docs` drops its `vec`: `distinct-by` already answers a vector.
- A backend test checks the order on a dictionary where nine lemmas share a form.

## Capabilities

### New Capabilities

### Modified Capabilities
- `examples`: "A generation request carries the word's part of speech and CEFR level" says which entry the lookup takes when several share the target.

## Decisions

- Order kept by reducing into a set of seen keys and a vector of kept elements, over sorting the map's values by input position afterwards (a second pass) or pulling in `medley` for its `distinct-by` (a new dependency for one call).
- The check sits in `test/backend/examples_test.clj` beside `a-bare-word-is-found-exact-first-whatever-the-rank`, on the dictionary its `with-dictionary` builds, over a unit test of `distinct-by`: the lookup order is what the spec names, and `utils` is no domain algorithm.
