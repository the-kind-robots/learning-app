# Tasks

## 1. Dual read

- [x] 1.1 `configured-db-auth-secret` — pure over an env map: new name, then old name, then checkout fallback, then throw naming `LEARNING_APP__DB_AUTH_SECRET`
- [x] 1.2 `db-auth-secret` def reads through it with `(System/getenv)`; `running-from-source?` behavior unchanged
- [x] 1.3 Transition comment names the scaffolding and the phase 2c removal condition

## 2. Verification

- [x] 2.1 Tests: new-only → new; old-only → old; both → new wins; neither + packaged → throw naming the new var; neither + checkout → "secret"
- [x] 2.2 Backend suite green (28 tests / 56 assertions), zero reflection warnings
- [x] 2.3 No infra files touched — unit/ExecStart rename is phase 2b (deb artifact)
