# One service owns the state directory

## Why

#224's container smoke observed that `learning-app-run` (User=webapp) and `learning-app-backup-db` (User=dbmaintainer) both declare `StateDirectory=learning-app`. systemd rechowns the whole state tree to the starting unit's user whenever the top-level ownership mismatches, so `/var/lib/learning-app` ownership ping-pongs between webapp and dbmaintainer depending on start order. It self-heals only because each start rechowns again; the deployer-owned dictionary directory gets silently rechowned too (GH-228).

## What changes

`learning-app-run` becomes the sole owner of `StateDirectory=learning-app` and pins `StateDirectoryMode=2770` (setgid, group-writable) so group-webapp members keep sqlite journal/-wal/-shm access without owning the tree. `learning-app-backup-db` drops its `StateDirectory` and `LogsDirectory` claims — it only needs the state path writable under `ProtectSystem=strict`, which `ReadWritePaths=/var/lib/learning-app` gives without any ownership semantics; it logs to the journal only. The tmpfiles.d entry for `/var/lib/learning-app` moves to 2770 to match.

## Impact

`infra/production/etc/systemd/system/learning-app-run.service`, `learning-app-backup-db.service`, `etc/tmpfiles.d/learning-app.conf`. Delta on `production-service-hardening`. Proves out in the #214 container smoke.
