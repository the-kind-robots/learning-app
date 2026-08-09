# postinst sets the CouchDB admin password via local.d, not HTTP

## Why

postinst set the admin password with an HTTP PUT that authenticates using the old password — it can only re-set a password it already knows. On any drift between the credstore and the running instance the PUT fails, and `|| true` hid that failure, leaving the credential and the database silently out of sync.

## What changes

- When the decrypted credential does not authenticate against the running CouchDB, postinst writes `[admins]` with the plaintext password into `local.d/99-admin-password.ini` (couchdb-owned, mode 640) and restarts CouchDB; CouchDB replaces the plaintext with a hash on startup. No old password is needed.
- When the credential already authenticates, nothing is written and CouchDB is not restarted.
- The `|| true` on the admin path is gone; a credential that still fails after the restart aborts postinst.
- The container smoke gains a drift scenario: rotate the credstore credential, reinstall, prove the rotated password is live and the old one dead, rotate back; smoke.sh asserts the written ini holds a hash, not plaintext.

## Impact

Capability: `production-service-hardening`. Files: `infra/production/DEBIAN/postinst`, `infra/production/DEBIAN/postrm` (removes the ini on purge), `infra/production/opt/couchdb/etc/local.d/10-learning-app.ini` (comment), `infra/staging/run.sh`, `infra/staging/smoke.sh`. Proof is the container smoke on PRs touching `infra/**`.
