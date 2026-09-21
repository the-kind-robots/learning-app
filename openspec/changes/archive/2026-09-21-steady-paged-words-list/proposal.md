## Why

The paged word list (#435) drives its own loading, and the review of it found five ways
the driving misfires (#439): the lookahead it documents does not exist, nothing orders
the five callers that write the list state, a page arriving in the background closes the
edit dialog, the sentinel observer is a single handle two effects fight over, and the
scroll reset fires on the keystroke instead of on the rows it is there to meet.

## What Changes

- The `IntersectionObserver` gets the scrolling list as its root, so `rootMargin` widens
  the box that actually clips the sentinel and the next page is asked for a screenful
  before the reader arrives. Today the margin widens the viewport, which never clips it.
- Every read of the list is sequenced: the newest request issued is the only one allowed
  to write. A page requested before a search can no longer land after it, replace the
  filtered rows and blank `:words/search` under a search box that still holds the query.
- A page arriving in the background no longer closes the open word. Closing the dialog
  moves to the actions that mean it — cancel, save, delete — instead of riding on every
  `:action/show-words`.
- The sentinel observer is stored beside the node it watches and disconnected only on a
  match, so a subtree swap — mount hooks run before unmount hooks — no longer lets the
  old node's unmount kill the new observer. (#418 keeps the broader point that runtime
  handles belong in the system, not in module atoms; this change only pairs them.)
- The scroll back to the top moves from the keystroke to the render that swaps the rows,
  400 ms later, so scrolling during the debounce window cannot leave the reader on the
  sentinel when the shorter filtered list arrives.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `vocabulary-list-paging`: when the next page is asked for (before the end, not at it),
  which read may write the list when several are in flight, when the list is scrolled
  back to its first row, and that a background page load leaves the open word open.

## Impact

- **Client**: `src/client/pages/words/effects.cljs` (observer root and pairing, read
  sequencing), `src/client/pages/words/actions.cljs` (dialog closing, scroll reset),
  `src/client/pages/words/presenter.cljs` (the query-changed predicate the actions ask
  for).
- **Tests**: `test/client/pages/words_actions_test.cljs`,
  `test/browser/words-paging.spec.js`.
- **Not in scope**: #441 (the dead paging path) and #440 (what paging costs the rest of
  the session).
