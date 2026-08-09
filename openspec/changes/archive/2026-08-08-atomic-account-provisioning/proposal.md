# No half-created accounts across SQLite and CouchDB

## Why

`create-account!` commits the user row before the CouchDB steps run, so a failed CouchDB step strands a row with no userdb and no token holder — dead weight that also blocks the id (#254).

## What changes

- The CouchDB steps (`db/use` + `db/secure`) move inside the provisioning transaction, next to the recycled-id guard that already runs there: a failed step throws, the insert rolls back, and the id is minted again by the next attempt.
- A userdb created by the failed attempt is removed with it (best effort) — otherwise the recycled-id guard would refuse the freed id on every retry.

## Impact

`src/backend/core.clj` (`create-account!`), `test/backend/core_test.clj` (+2 tests). Spec: identity-provisioning.
