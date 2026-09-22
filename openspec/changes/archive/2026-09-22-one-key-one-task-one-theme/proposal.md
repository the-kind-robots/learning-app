## Why

Three rules were carrying weight that the values themselves can carry.

The cache key covers the question and the generation, but not the dictionary
metadata that also goes into the prompt — so two different prompts could share
a key. That was patched by refusing to store an answer generated without
dictionary metadata, which loses the saving exactly when it is worth most: a
dictionary that is away makes every device pay for the same sentence.

"Already queued" was a question asked of the database: read the live queue,
build the set of pairs, subtract. A fetch is one pair of (entry, collection),
which is an identity, not a search.

And a pass was told not to unfold a collection it pulled, so an entry themed on
another device waited for the next start. The unfolding costs the size of the
theme — the same read the start does anyway — and the wait was the price.

## What Changes

- The cache key covers what the dictionary said about the word, `nil`
  included. A word arriving in the dictionary is a miss, like an edited prompt.
- An example generated while the dictionary was away is kept like any other.
- A fetch task's id is the pair it is for, so asking twice writes once and no
  one reads the queue to find out what is in it. A dead-lettered fetch is kept
  under a key of its own, leaving the pair askable again.
- A pass unfolds a collection it brought into the entries that collection
  names.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `example-cache`: the dictionary metadata joins the key, and the rule that
  withheld degraded answers is gone.
- `example-backfill`: one task per pair by identity, and a theme a pass brings
  is unfolded.

## Impact

- **Backend**: `src/backend/examples.clj` (the generation version carries the
  word metadata; `cacheable?` and its marker are gone), `src/backend/core.clj`.
- **Client**: `src/client/tasks.cljs` (task ids, dead letters aside),
  `src/client/adapters/examples.cljs` (fetch ids, no queue read),
  `src/client/ports/examples.cljs`, `src/client/use_cases/examples.cljs`.
- **Cost**: a backfill reads the collections and the entries and no longer
  reads the queue; a pass that brings a theme reads that theme's entries.
