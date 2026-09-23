## Why

The backfill runs after every completed replication pass, and every run reads the whole vocabulary,
every collection, every example and the whole fetch queue. A pass runs at most every 30 seconds and
a push-only pass carries nothing home, so a device that is merely syncing its own writes pays that
read over and over, and the price grows with the vocabulary it holds.

## What Changes

- The full scan runs once, at start: the device closes whatever debt earlier runs left.
- After a replication pass the debt is counted only over what that pass brought home. A pass that
  pulled nothing reads nothing.
- A pass creates debt in two ways, and both are counted: a word that arrived needs an example for
  each collection holding it, and a collection that arrived needs one for each word it names — a
  word already on this device can be put into a theme on another device, and this device has no
  example for that pair.
- The cap per pass stays. What it leaves over is queued by a later pass that pulls, or by the next
  start.

## Capabilities

### New Capabilities

<!-- None. -->

### Modified Capabilities

- `example-backfill`: when the device counts its debt, and over what. The rule for which pairs are
  owed is unchanged.

## Impact

- **Client**: `src/client/db/pouch.cljs` (a pass reports the ids its pull wrote),
  `src/client/sync.cljs` (the hook is handed the pass result),
  `src/client/use_cases/examples.cljs` (the full pass at start, the scoped pass after replication,
  and the single statement of the rule), `src/client/use_cases/vocabulary.cljs` and
  `src/client/ports/examples.cljs` (the duplicate-word path asks that same rule instead of its own
  lookup), `src/client/main.cljs` (the component starts the backfill and hands back the hook).
- **Cost**: a pass now reads the collections it holds plus the words the pass named, instead of the
  whole vocabulary; a push-only pass reads nothing.
