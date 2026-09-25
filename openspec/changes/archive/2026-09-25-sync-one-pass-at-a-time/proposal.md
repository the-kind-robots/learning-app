# Proposal: sync-one-pass-at-a-time

## Why

Route entry, the `online` event, the poke socket and the local-write feed
each started a replication pass of their own, with nothing to stop them
overlapping (#319). The 30 s gate on route entry, and the `dirty` flag it
reads, were a second scheduler beside them. And a pull's own writes counted
as local writes, so a pass that pulled anything queued another.

## What Changes

- One pass at a time. A request while no pass runs starts one. A request
  while a pass runs marks one more pass to follow it, however many arrive.
- Route entry no longer skips a pass within 30 s of the last one. The gate,
  `dirty`, `last-pass`, `pull-due?` and `pass-interval-ms` go.
- Local writes still reach the scheduler through the throttled change feed
  (3 s), so a lesson's reviews go out in a few passes, not one per review.
- A change the pass itself pulled is not a local write and requests nothing.
  The pass reports the revisions it pulled, and the change feed checks each
  change against them.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `push-sync`: the 30 s route-entry gate is replaced by one pass at a time
  with a single follow-up pass.

## Impact

- `src/client/sync.cljs`: scheduling in `start!`.
- `src/client/db/pouch.cljs`: `sync-once!` also reports `:pulled-revs`.
- `test/client/sync_pull_test.cljs`.
- `docs/dev/mobile-pwa-testing.md`: the `pull-skipped` trace goes.
