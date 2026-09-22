## Why

Review of the example work found the cache and the backfill each carrying a rule that costs more
than it buys. The backfill's cap of twenty per pass guards against a burst the task queue already
prevents, and it leaves a remainder nobody is responsible for. A pass unfolds a collection it
pulled into every entry that collection names — and a theme document is rewritten whole every time
anyone adds one entry to it anywhere. And a cached sentence is keyed by the request alone, so the
prompt can be edited and every stored answer goes on being served, with nothing to invalidate.

The same review found three ways the endpoint could be worse than no cache at all: a SQLite error
refusing a request that would have generated, a sentence generated from raw glosses but keyed on
normalized ones, and a sentence generated without the dictionary's metadata kept for everyone.

## What Changes

- A backfill queues every missing pair, with no cap, in one write. A start leaves no remainder.
- A pass counts only the entries it brought. A collection among them is not unfolded; an entry
  themed on another device gets its example at the next start.
- A dead-lettered fetch no longer counts as queued, so its pair stops being blocked forever.
- A pass does not wait for the backfill, and a failing backfill cannot fail a pass.
- The cache key covers the generation — the system prompt and the configured models — so editing
  the prompt is a miss rather than a stale row, and the digest material no longer depends on
  Clojure's print settings.
- A failing cache is a miss, never a refused request; the glosses the key is built from are the
  glosses the prompt is built from; an example generated without dictionary metadata is served but
  not stored.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `example-backfill`: no cap, no unfolding of a collection a pass brought, dead letters not counted
  as queued, and a pass that does not wait.
- `example-cache`: the generation in the key, print-independent material, a cache that cannot refuse
  a request, and no storing of a generation the dictionary did not answer for.

## Impact

- **Client**: `src/client/use_cases/examples.cljs`, `src/client/adapters/examples.cljs`,
  `src/client/ports/examples.cljs`, `src/client/tasks.cljs`, `src/client/sync.cljs`.
- **Backend**: `src/backend/examples/cache.clj`, `src/backend/examples.clj`, `src/backend/core.clj`.
- **Cost**: a start writes one bulk document write instead of one insert per pair; every request
  hashes the prompt along with the question, which is one SHA-256 over a few kilobytes.
