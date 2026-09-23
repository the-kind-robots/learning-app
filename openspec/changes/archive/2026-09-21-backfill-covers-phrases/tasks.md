# Tasks

## 1. Say it in the spec

- [x] 1.1 `example-backfill` speaks of vocabulary entries where it said "word": what a pass brings,
      what a named collection wants, what a start reads.

## 2. Pin it down in tests

- [x] 2.1 `test/client/support/db_seed.cljs` seeds a phrase (`:kind`).
- [x] 2.2 A phrase the pass brought is in `missing-examples` and gets a task carrying its Russian
      glosses, not an empty list.
- [x] 2.3 A phrase whose example is already here gets no task.
- [x] 2.4 A phrase in a named collection wants that collection's own example, and nothing once it
      has one.

## 3. Delivery

- [x] 3.1 Client node tests and the browser suite run.
- [x] 3.2 Sync the specs and archive the change on this branch.
