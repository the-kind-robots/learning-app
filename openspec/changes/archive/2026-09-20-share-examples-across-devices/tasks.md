# Tasks

## 1. Server cache

- [x] 1.1 `resources/migrations/003-example-cache.sql` — the cache table.
- [x] 1.2 `src/backend/examples/cache.clj` — normalize a request, digest it, read and write a row.
- [x] 1.3 `src/backend/core.clj` — `/api/examples` reads the cache before generating, writes a valid
      result after.
- [x] 1.4 Tests: a hit never calls the provider, a miss stores, an invalid result stores nothing,
      gloss order and context fold into the key as specified.

## 2. Client backfill

- [x] 2.1 `src/client/adapters/examples.cljs` — the pairs an example-fetch task is already queued for.
- [x] 2.2 `src/client/ports/examples.cljs` — expose it.
- [x] 2.3 `src/client/use_cases/examples.cljs` — the pure selection of owed pairs, and the use case
      that reads, selects and queues.
- [x] 2.4 `src/client/sync.cljs` — a completed pass calls the hook it was handed, contained.
- [x] 2.5 `src/client/main.cljs` — wire the hook over the words, collections and examples ports.
- [x] 2.6 Tests: owed pairs for words without an example, none for words with one, the cap, the
      dedup against queued tasks, and a pass that still reports when the hook throws.

## 3. Delivery

- [x] 3.1 `clj -M:test` and the shadow-cljs node tests pass.
- [x] 3.2 Sync the specs and archive the change on this branch.
