# Tasks

## 1. Examples in user-db

- [ ] 1.1 The example schema names `:user/db`; `documents/example-doc` builds the document and its id from the pair and the content
- [ ] 1.2 `save-example!` writes through `insert-if-absent`; the one copy of that rule lives in `lib/db`
- [ ] 1.3 The fetch task reads `documents/example-id-prefix` with `db.pouch/id-with-prefix?` before its request
- [ ] 1.4 `move-device-examples!`: every device-db example to user-db, a page of 100 at a time; `examples-moved!` retries through `db.pouch/retried`

## 2. Ordering

- [ ] 2.1 `use-cases.examples/start!` derives the first pass from `:sync/account-id` and its pass listener
- [ ] 2.2 The start's backfill and each pass's backfill wait for the move and the first pass; fetches are held by `example-fetch/hold-until!`; the task queue starts once memory is loaded
- [ ] 2.3 A pass's fetches are due `pass-fetch-delay-ms`; a pass's examples cancel their fetches (`cancel-answered!`)
- [ ] 2.4 A pass's backfill catches memory up and reads from memory only
- [ ] 2.5 A lesson takes one example per word, the smallest id

## 3. Memory follows user-db alone

- [ ] 3.1 Memory keeps one position; the loader reads and follows `:user/db` only
- [ ] 3.2 Snapshot header flat, `format-version` 2; the golden fixture carries an example id of the new form
- [ ] 3.3 `forget-account-data!` and the docs that called examples device-local

## 4. Verify

- [ ] 4.1 Node: the move (distinct kept, identical collapsed, foreign deleted, paged, interrupted, retried), one document per distinct example on two devices with no conflict, moved and fetched copies converge, the gate, the pass delay and cancel, the prefix against `visible-in`, the held fetch
- [ ] 4.2 Browser: a start sends a request only for the word with no example anywhere, and never queues one for the others; device-db examples move
