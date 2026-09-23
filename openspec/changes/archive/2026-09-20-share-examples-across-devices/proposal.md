## Why

Example sentences live in `device-db`, which has no copy on the server and never replicates. A word
added on the phone arrives on the desktop by replication with no example, and nothing on the desktop
ever asks for one — the only example-fetch trigger is adding a word. When a second device does ask,
the request goes to the generation provider again: the same word, the same glosses, the same
collection, paid for twice and answered with a different sentence.

## What Changes

- The server keeps a cache of generated examples, keyed by the request that produced one — German
  word, the confirmed Russian glosses, the optional collection context. `GET /api/examples` answers
  a hit from the cache and never reaches the provider; a miss generates as it does today and stores
  the result. Only a valid result is stored.
- The cache is shared by every account and has no expiry: a generated sentence is not private data,
  and sharing it is the whole saving. The collection name is part of the key and nothing else.
- After a replication pass the device queues example fetches for the words it holds without one, so
  a word that arrived by replication ends up with the same example the device that added it got.
- A backfill pass queues a bounded number of fetches; the remainder rides the next pass. The queue
  runs three tasks at a time, and an unbounded first sync would otherwise turn a whole vocabulary
  into one burst of requests.
- No invalidation: changing a word's translation changes the key, so the next request is a miss and
  generates afresh. Regenerating the example a word already has when its translation changes is
  #415 and is not part of this change.

## Capabilities

### New Capabilities

- `example-cache`: server-side reuse of generated examples, keyed by the request — what is stored,
  what counts as the same request, what is never stored.
- `example-backfill`: the client fills in examples for words it did not add itself, after a
  replication pass, bounded per pass.

### Modified Capabilities

<!-- None. The existing `examples` and `examples-schema` requirements — when a fetch task is created
     on word creation, what an example document holds, how a collection scopes it, and that the
     endpoint authenticates first — all hold unchanged. -->

## Impact

- **Backend**: `resources/migrations/003-example-cache.sql` (new table), `src/backend/examples/cache.clj`
  (new), `src/backend/core.clj` (`/api/examples` reads the cache before generating and writes it after).
- **Client**: `src/client/use_cases/examples.cljs` (new — which fetches a device owes),
  `src/client/adapters/examples.cljs` (the pairs a fetch is already queued for),
  `src/client/ports/examples.cljs`, `src/client/sync.cljs` (a pass calls the hook it was handed),
  `src/client/main.cljs` (wires the hook).
- **Cost**: one SQLite read per example request, replacing a provider call whenever the same request
  was answered before.
