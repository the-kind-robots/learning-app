## 1. Prove the defect

- [x] 1.1 Extend `test/client/db_migrations_test.cljs` with cases seeding more than 25
      documents of a type and asserting every one reaches the destination
- [x] 1.2 Run the client tests against unfixed code and record the failure

## 2. Read every document

- [x] 2.1 Use `db/find-all` in `copy-type!`
- [x] 2.2 Use `db/find-all` in the task-data payload rewrite
- [x] 2.3 Confirm the new cases pass

## 3. Close out

- [x] 3.1 Full client test suite green
- [x] 3.2 `openspec validate` clean, change archived on the branch
- [x] 3.3 PR opened, issue moved to In review
