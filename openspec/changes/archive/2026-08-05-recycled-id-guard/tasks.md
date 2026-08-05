# Tasks

- [x] 1. Existence check inside the account transaction; refusal keeps the id unspent
- [x] 2. Provision route: 503 + telemetry on refusal
- [x] 3. Tests: refusal rolls back the row; a fresh id provisions and secures its userdb
- [x] 4. Live proof on the stand: stale userdb-2 → 503, row count unchanged; cleared → 200, role-secured userdb
