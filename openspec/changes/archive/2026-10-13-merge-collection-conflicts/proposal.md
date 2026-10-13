## Why

Sync resolves conflicts for words only. A collection edited on two devices before they sync keeps two live versions, and PouchDB serves one by revision tree, not by intent: a word added to a theme on one device can be missing from it, and deleting a conflicted theme removes one version so the other comes back (#525).

## What Changes

- A collection carries `renamed-at`, stamped on creation and on rename.
- After a pull that wrote a collection, the client merges its live versions: word ids united, name from the version with the latest `renamed-at`, losing versions removed.
- Deleting a collection removes every live version. A collection deleted on one device and edited on another comes back with the edit.

## Capabilities

### New Capabilities

### Modified Capabilities
- `collections-data-model`: a conflicted collection merges, and deleting a collection removes every version.
- `push-sync`: a pull that wrote collections resolves their conflicts.

## Impact

- `src/client/sync.cljs`, `src/client/db/pouch.cljs`, `src/client/adapters/learner.cljs`, `src/client/domain/collections.cljs`, the sync and collection tests.
