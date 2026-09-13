## 1. Engine

- [x] 1.1 Each adapter declares a schema value `{:type :db :indexes :views}`; `db.pouch/init!` takes the list, routes by `:db`, ensures its own `by-type` index on every database that holds a schema, every declared index and one design document per declared view (`<name>/rows`), rewritten only when the map source differs
- [x] 1.2 Adapters per aggregate (`words`, `reviews`, `lessons`, `collections`, `examples`) and ports per aggregate; every repository speaks `:id` outward; the words-with-retention join lives in `use-cases.vocabulary`; a layering test checks the barrier against the tree

## 2. Indexes and views

- [x] 2.1 `by-type-word-id` on user-db (reviews) and `by-type-word-id`, `by-type-collection-id` on device-db (examples); `examples/list` reads with `find-all`
- [x] 2.2 `_design/reviews-by-word` (`word_id` → `[created_at retained]`) and `_design/vocab-preview` (`_id` → `[kind value translation]`); retention levels and word lists read view rows, by keys when the caller narrowed the words and whole otherwise
- [x] 2.3 `db/sync` passes a `filter`; `sync-once!` keeps `_design/` documents out of both directions

## 3. Sync

- [x] 3.1 A pass reports `{:pulled :pushed}`; the current screen reloads and the pairing dialog is checked only when something was pulled
- [x] 3.2 Route entry runs no pass within 30 s of the last completed pass with nothing written locally since; a poke and the window's `online` event always pass

## 4. Themes screen

- [x] 4.1 The screen switches at once and shows a loading state until its collections are read; a reload with equal data saves the same map and does not render; a failed read clears the loading state
- [x] 4.2 Cards own their touch gestures (`touch-action: manipulation`, no text selection, no callout); a `pointercancel` after at most 10 px of travel and 2 px of scroll is a tap; a fired gesture swallows the click that may follow

## 5. Development trace

- [x] 5.1 A ring of the last 500 events (errors, lifecycle, long tasks, actions, effects, renders, pointer steps on the themes screen, tap recovery, skipped pulls), mirrored to localStorage on errors and visibility changes, exported by the red D; `^boolean` on every `goog/DEBUG` test so a release build drops it all

## 6. Verification

- [x] 6.1 Unit tests: view rows per word, `ensure-views!` rewriting only a changed map, list retention equal to per-word retention over 35 reviews, `_design/` staying behind in both directions, the tap decision, the throttle, the no-render reload
- [x] 6.2 Browser: the loading state precedes the first card; `getIndexes()` after start-up lists the indexes; per-query ms and first paint at ~10 000 docs on a synthetic dataset
