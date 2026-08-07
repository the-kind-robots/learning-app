# A backup counts only when it restores

## Why

The staging rung proves an archive exists and names both stores (#211) — it never proves the archive restores into a working system. The runbook's "Restore from backup" section has never been executed; untested restore procedures are where backups go to die. And the borg passphrase — the single value whose loss makes every archive unreadable — is documented nowhere (#191).

## What changes

`infra/staging/restore-drill.sh`, run by `run.sh` after the smoke: seed a real account (grant → provision endpoint) and a userdb doc, run the backup unit, destroy both stores (rm the SQLite file, DELETE the userdb), restore from the borg archive exactly as the runbook prescribes, then assert coherence — the seeded token gets 200 from `/auth/check`, the userdb serves the doc through nginx, the boot orphan report (#205) is clean. PASS/FAIL per assertion, nonzero exit on FAIL. The runbook section is rewritten as the executable procedure the drill revealed: extract as `dbmaintainer`, explicit ownership on both stores (borg cannot restore it from a non-root extract), stale `-wal`/`-shm` removal, wholesale CouchDB tree replacement, CouchDB-before-app start order. Borg key custody lands in `server-configuration.md` (where the passphrase lives, that losing it loses all archives, the `borg list` dry check) with the check echoed in `verification.md`.

The drill's first run surfaced a config split: the app carries its CouchDB admin credentials hardcoded (`lib/db/src/db.cljc` `conn`), so with the container's previous `staging-couch-pass` the app could neither create userdbs (provisioning answered 503) nor run the boot reconciliation report (skipped on 401). The staging preseed now matches the app's value — the only configuration under which the packaged shape actually works.

## Impact

New `infra/staging/restore-drill.sh`; `run.sh` gains the drill phase; `docs/ops/runbook.md` restore section corrected; `docs/ops/server-configuration.md` + `docs/ops/verification.md` gain borg-key custody; delta on `backup-pipeline`.
