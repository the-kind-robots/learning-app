# Tasks

- [x] 1. `db-spec` reads `LEARNING_APP__DB_PATH` with `app.db` default
- [x] 2. Confirm `db-spec` is the only home of the app database spec (grep migrations, tools, tests)
- [x] 3. Backend tests green (11 tests / 26 assertions)
- [x] 4. Prove env var: boot migrations with `LEARNING_APP__DB_PATH=/tmp/probe-192.db`, file appears at the pointed path; default spec unchanged without the var
