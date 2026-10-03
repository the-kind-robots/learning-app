# Tasks

## 1. Memory

- [x] 1.1 The loader notes the feed positions, reads both databases (the types memory keeps), reads what was stored meanwhile, and hands memory over once (`:effect/memory-loaded`)
- [x] 1.2 It follows both change feeds (`db.pouch/follow-changes`) into `:effect/memory-changed`, and catches up on `visibilitychange` (`loader/catch-up!`)
- [x] 1.3 A document memory cannot take is left out and logged
- [x] 1.4 A failed read at start sets `:learner/read-failed?`, and the splash says so

## 2. Writes

- [x] 2.1 `db.pouch/write-latest!` reads a document by id and puts over the winner, once more on a conflict; the learner's writes and the task queue use it
- [x] 2.2 A learner write reads what it wrote back into memory (`db.pouch/read-ids`)
- [x] 2.3 `delete-word!` writes the word's deletion and its collections in one bulk write, retries once, and on a second refusal reports and puts the word back into its collections
- [x] 2.4 `delete-collection!` deletes the collection document only
- [x] 2.5 A name check and a word's collections read memory after it has caught up
- [x] 2.6 Adding a word with reviews writes no initial review; an example is fetched only when the collection has none

## 3. Backfill

- [x] 3.1 At start it waits for the load; after a pass it reads the pulled documents by id, leaves out deletions, and weighs entries a chunk per task

## 4. Indexes

- [x] 4.1 user-db declares the `type` index only; the views are gone

## 5. Verify

- [x] 5.1 Property and node tests for the load, the feed, catching up, writes, deletion, re-adding and the backfill
- [x] 5.2 Browser suites
