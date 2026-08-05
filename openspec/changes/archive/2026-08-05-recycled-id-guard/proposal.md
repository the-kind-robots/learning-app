# Recycled account ids do not inherit userdbs

## Why

Account ids are `users` row ids and the CouchDB database name derives from them, but the two stores have independent lifetimes: an `app.db` reset restarts id allocation while `userdb-*` survive. Seen live (#182): a freshly provisioned account received id 1 and its first sync pass pulled 112 documents belonging to the previous owner of `userdb-1`.

## What changes

`create-account!` refuses an id whose userdb already exists — the row insert and the existence check share a transaction, so a refusal leaves the id unspent and the stale userdb intact as evidence. The provision route answers 503 and logs `::recycled-id-refused`. Deleting the stale userdb is the operator's call, not the code's.

## Impact

`create-account!`, the provision route, two tests, live verification on the dev stand. Known cost: the invite is burned before the refusal is discovered — the operator mints a new one after clearing the conflict.
