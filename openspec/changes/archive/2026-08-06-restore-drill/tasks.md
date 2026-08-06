# Tasks

- [x] 1. restore-drill.sh: seed (grant → provision → userdb doc), backup unit run, destroy both stores, restore per runbook, coherence asserts (auth 200, doc via nginx, orphan report clean) — PASS/FAIL per line, nonzero exit on FAIL
- [x] 2. run.sh runs the drill after the smoke; both exit codes surface
- [x] 3. Runbook "Restore from backup" rewritten as the executable procedure (ownership, -wal/-shm, wholesale couch tree, service order)
- [x] 4. Borg key custody documented: credential location, loss consequence, rotation, `borg list` dry check (server-configuration.md, verification.md)
- [x] 5. Real run; drill output in the PR
