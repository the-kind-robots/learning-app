# The deb's CouchDB config applies on install, not on the next accident

## Why

#224's container smoke observed that postinst only ever `enable --now`s CouchDB. The deb ships `/opt/couchdb/etc/local.d/10-learning-app.ini` (proxy auth handlers), but CouchDB reads `local.d` at startup — so on an upgrade where CouchDB is already running, a changed ini rides the deb and takes effect only on the next unrelated restart. True on production today (GH-229).

## What changes

postinst hashes the shipped ini against a stamp from the previous install and restarts `couchdb.service` when they differ, updating the stamp. The restart sits between `enable --now` and the existing wait-loop, so readiness and admin configuration run against the instance that has the shipped config loaded. Same ini, no restart — repeat installs stay idempotent.

## Impact

`infra/production/DEBIAN/postinst` only; stamp lives in `/var/lib/misc/learning-app-couchdb-ini.sha256`. Delta on `deploy-pipeline`. Proves out in the #214 container smoke.
