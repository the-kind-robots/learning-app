## Why

A start from the snapshot takes the 220,038 entries of a 20k-word, 200k-review vocabulary and adds each through the path a document takes: about 2.9 s of a 3.2 s start (#508). A snapshot that holds memory as it was built skips that work, so the start is the decode and the catch-up.

## What Changes

- The snapshot holds memory itself instead of the entries memory took from each document. A snapshot of the earlier format is dropped; each device reads every document once after the deploy.
- Memory no longer keeps what it took from each document, nor each document's revision. It finds the version it holds by the document's id. A revision memory holds already is removed and added again, and gives equal memory, at most one more render.

## Capabilities

### New Capabilities

### Modified Capabilities
- `learner-data-memory`: the snapshot requirement says what the snapshot holds and that restored memory reads as a full read; the requirement on memory no longer promises that a repeated revision leaves the memory value untouched.

## Impact

- `src/client/adapters/learner/memory.cljs`, `src/client/adapters/learner/snapshot.cljs`, `src/client/adapters/learner/loader.cljs`, the memory tests.
- ADR-0023 records the choice (ADR-0018 chose entries).
