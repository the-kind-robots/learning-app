# CouchDB joins the backup archive

## Why

`backup.sh` archived exactly one artifact — the SQLite snapshot — while every user's vocabulary, reviews and collections live in CouchDB userdbs with no backup at all (#206). Local-first softens this (devices can re-seed), but a user whose only device dies loses everything if the server copy is also gone.

## What changes

One borg archive (`app-<now>`) now carries both stores, taken back to back — a restore is coherent by construction. The backup service gains read access to `/var/lib/couchdb` via `SupplementaryGroups=couchdb`, scoped to the unit rather than the user. Runbook gains the restore procedure with the coherence rule.

## Impact

`backup.sh`, backup unit, runbook. Real proof is the next backup run and #191's restore drill.
