# Tasks

## 1. Projection

- [x] 1.1 `db.pouch/follow!`: update_seq, every document, then the live feed since that seq with docs; batches handed over in one call
- [x] 1.2 `db.pouch` writes report what they wrote, with the new `_rev`, after PouchDB resolves; a refused write reports nothing
- [x] 1.3 `adapters.memory`: add and remove functions per document type — held version out, current version in — keeping the words in list order (normalised search text, review history), examples by id and by word, collections; equal `_rev` changes nothing; a deletion removes; a review in or out makes a new history
- [x] 1.6 Property test: incremental ingest in any batches equals a rebuild from the final documents
- [x] 1.7 Measure the memory shape at 1.5k and 20k words before choosing it (five designs, ADR-0016)
- [x] 1.4 `main`: memory component writes `:learner/memory` and `:learner/ready?` into the store, one `swap!` per batch
- [x] 1.5 Node tests: load, feed, same rev, deletion, review tombstone, write-through, refused write

## 2. Screens from memory

- [x] 2.1 Pure use-case reads over memory: home summary, collections summary, vocabulary rows (alphabetical, most due, search, scope), lesson start
- [x] 2.2 Home, collections, lesson entries compute synchronously from memory in one save
- [x] 2.3 A memory change recomputes the page on display in the same store write; the post-pull reload goes
- [x] 2.4 Startup: a screen opened before memory is ready claims nothing and fills in when ready
- [x] 2.5 Words entry, filter (no debounce) and paging from memory, on #504's shape

## 3. Transitions

- [x] 3.1 Close to home renders home before `history.back()`; the route's start only refreshes when home is already on display
- [x] 3.2 `:effect/after-paint`; sync pull on entry goes there
- [x] 3.3 The lesson lives in app state only: no lesson document, port or adapter; answer checks and advances take the lesson from app state

## 4. Verify

- [x] 4.1 Browser: per transition, the target screen's data is in the DOM at the first rAF after the click, on ~1500 words / ~7100 reviews
- [x] 4.2 Browser: own write then navigate; a second tab's write; a replicated write from another database; the #486 race; a screen opened before memory is ready fills in; #359 empty states
- [x] 4.3 Before/after table, release build, at 1.5k/7.1k and 20k/200k: click → data, render, boot → memory ready, write → memory
- [x] 4.4 Node suite, release compile with no warnings, full browser suite
