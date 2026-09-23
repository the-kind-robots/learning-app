## Why

Since #371 a phrase asks for an example exactly as a word does, and `specs/examples/spec.md` says so:
a fetch is created for a vocabulary entry, "whether it is a word or a phrase". `example-backfill`
still says "word" in every requirement, so a phrase that arrives by replication reads as out of
scope. The code never distinguished them — one document type, one preview view, one translation
shape — so the spec, not the behaviour, is what is behind.

## What Changes

- The backfill requirements speak of vocabulary entries, words and phrases alike: what a replication
  pass brings, what a start reads, and what a named collection wants of its own.
- Nothing else. Which pairs are missing an example, the cap, the containment and the two triggers
  are unchanged.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `example-backfill`: a phrase is a vocabulary entry the backfill covers, said where it was only
  ever implied.

## Impact

- **Specs**: `openspec/specs/example-backfill/spec.md`.
- **Client**: none — `test/client/examples_backfill_test.cljs` gains the phrase cases that pin the
  behaviour down; `test/client/support/db_seed.cljs` can seed a phrase.
