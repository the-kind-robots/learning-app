# Tasks

## 1. The backfill

- [x] 1.1 Drop the cap: `missing-examples` names every missing pair.
- [x] 1.2 A pass counts the entries it brought; the unfolding of a pulled collection goes, and with
      it the function that did it.
- [x] 1.3 One bulk write for a backfill's tasks (`tasks/create-tasks!`, `:examples/request-many!`).
- [x] 1.4 `pending-requests` asks by `tasks/alive`, so a dead letter stops blocking its pair.
- [x] 1.5 A pass calls the hook without awaiting it, guarded on both sides.
- [x] 1.6 Tests: a start queues 120 of 120; a pass carrying only a collection queues nothing and the
      next start queues the themed pair; a dead-lettered fetch does not count as queued; a pass does
      not wait and does not fail.

## 2. The cache

- [x] 2.1 `lookup` and `store!` answer a miss and a dropped row when the table cannot be reached.
- [x] 2.2 The endpoint generates from the glosses the key was built from; one `examples/glosses`.
- [x] 2.3 The key covers `examples/generation-version` — the prompt and the configured models — and
      its material is JSON, not `pr-str`.
- [x] 2.4 An example generated without dictionary metadata is served and not stored.
- [x] 2.5 Tests: the table dropped under a live server; an edited prompt is a miss; a bound
      `*print-length*` keeps two questions apart; a degraded generation is not kept.

## 3. Delivery

- [x] 3.1 Backend, client node and browser suites run.
- [x] 3.2 Sync the specs and archive the change on this branch.
