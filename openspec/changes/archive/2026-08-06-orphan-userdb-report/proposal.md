# Reconciliation report: orphan userdbs

## Why

The per-provision guard (#197) catches a recycled id at the moment of harm, but nothing checks the two stores against each other: every `userdb-N` in CouchDB should have a matching `users` row in SQLite. Orphans accumulate silently — leftovers of deleted accounts, or evidence of an `app.db` reset.

## What changes

A reconciliation **report**, not an auto-repair: enumerate CouchDB databases, keep the `userdb-N` names, diff `N` against `SELECT id FROM users`, log the orphans at startup and expose the same function to the REPL operator. CouchDB being down logs a warning and never blocks boot.

Auto-tombstoning is forbidden by design: SQLite restored from an older backup makes every account created after that backup a live, legitimate userdb with no row — auto-deleting at that moment destroys real user data exactly when the system is most fragile. The same applies to a half-finished #201 migration. Orphans are evidence; disposal is a human decision. The opposite direction — rows without userdbs — is a different signal and out of scope.

## Impact

New `reconciliation` namespace, `db/all-dbs` in lib/db, one call in `-main`, four tests. No HTTP surface — the operator story is the REPL, like `mint-grant!`.
