# Tasks

- [x] 1. db-spec reads LEARNING_APP_DB_PATH (absorbed from #196, authorship preserved)
- [x] 2. Startup adoption: crash-safe move, no-clobber, loud logs both ways
- [x] 3. Tests: move with wal, no-clobber, no-legacy, same-path dev
- [x] 4. Simulation: marker row survives the move, boot 200, graceful stop
- [ ] 5. Real deploy: `database-adopted` in the journal, `/auth/check` 200 with an existing token, backup contains real rows
