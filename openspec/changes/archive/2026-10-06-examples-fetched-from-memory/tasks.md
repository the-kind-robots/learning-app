# Tasks

## 1. Remove the stored queue

- [x] 1.1 Delete `tasks`, `ports.task-queue` and their tests; the task machinery of `adapters.example-fetch`, `adapters.learner` and `use-cases.examples`; the call in `use-cases.vocabulary`; the `task-data-payload` migration
- [x] 1.2 `sync`: drop the pass listeners, `:sync/push-interval-ms` and `:pulled-ids`; return `:sync/first-pass`; `forget-account-data!` names examples only
- [x] 1.3 `db.pouch`: drop `id-with-prefix?`, `ensure-index!` and the helpers only the queue used; add `delete-index!`
- [x] 1.4 Test support builds no index; the layering test drops `tasks` and `ports.task-queue`

## 2. Tidying device-db

- [x] 2.1 `tidy-device-db!` moves examples, deletes every task a page per write, removes the queue's and the old `by-type` index

## 3. The rule

- [x] 3.1 `domain.examples` (`visible-in`, `missing-pairs`)

## 4. The fetcher

- [x] 4.1 `adapters.example-fetch`: `fetch-one` with an AbortSignal and failure kinds; online and visibility listeners
- [x] 4.2 `ports.examples`; `:clock/after`; `:learner/save-examples!`
- [x] 4.3 `use-cases.examples/start!`: readiness, the loop, pacing, the failure table, saving, visibility, `wake!`
- [x] 4.4 `:examples/fetcher` in `main` replaces `:worker/task-runner` and `:examples/backfill`
- [x] 4.5 `use-cases.collections/clamped` does not cut a character in half

## 5. Verify

- [x] 5.1 Measure a chunk of the scan on a 20k synthetic memory
- [x] 5.2 Node: the rule, the scan, readiness, one at a time, wake, saving, every row of the failure table, pauses, pacing, timeout, visibility, `fetch-one`, `:sync/first-pass`, tidying, saving
- [x] 5.3 Browser: a hidden tab, 429, 502, tidying at start; specs that relied on tasks
