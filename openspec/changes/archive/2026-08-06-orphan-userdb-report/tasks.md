# Tasks

- [x] 1. `db/all-dbs` in lib/db (GET /_all_dbs, admin conn)
- [x] 2. `reconciliation/orphan-userdbs` — diff userdb names against users ids; `reconciliation/report!` — log, non-fatal on CouchDB down
- [x] 3. Startup hook in `-main` after migrations
- [x] 4. Tests: orphan named exactly; matched stores → empty; rows without dbs not reported; CouchDB down → nil, no throw
- [ ] 5. Live proof on the stand: staged orphan userdb appears in the startup log and in the REPL report, nothing else does
