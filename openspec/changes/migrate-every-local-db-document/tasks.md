## 1. Prove the defect

- [x] 1.1 Extend `test/client/db_migrations_test.cljs` with a case seeding more than 25
      documents of a type and asserting every one reaches the destination
- [x] 1.2 Run the client tests against unfixed code and record the failure

## 2. Read every document

- [x] 2.1 Use `db/find-all` in `copy-type!`
- [x] 2.2 Use `db/find-all` in the task-data payload rewrite
- [x] 2.3 Confirm the new test passes

## 3. Repair browsers that recorded a truncated run

- [x] 3.1 Split the copy work out of `run-local-db-split!` and the rewrite work out of
      `run-task-data-payload!` so both can run under a different marker
- [x] 3.2 Skip source documents whose id the destination already knows, tombstones
      included, so a deleted word is not resurrected
- [x] 3.3 Add the `migration:local-db-split-repair` entry to the migration list
- [x] 3.4 Test: a marker present with documents left behind copies the remainder
- [x] 3.5 Test: a word deleted after the truncated run stays deleted
- [x] 3.6 Test: the repair writes its marker and returns `:already-complete` on a second
      run

## 4. Close out

- [x] 4.1 Full client test suite green
- [ ] 4.2 `openspec validate` clean, change archived on the branch
- [ ] 4.3 PR opened, issue moved to In review
