## 1. Tests

- [x] 1.1 Backend tests: request, repeat, malformed, rate limit, failed mail, pages change nothing, grant provisions, decline, decide twice, unknown token, status

## 2. Backend

- [x] 2.1 Migration: `invite_requests` table
- [x] 2.2 `invite-requests` namespace: store, decide, mail, the operator pages
- [x] 2.3 Routes in `core`, per-IP limit
- [x] 2.4 Production unit: the Worker secret as a systemd credential; `admin-setup.sh` asks for it

## 3. Worker

- [x] 3.1 `infra/cloudflare/invite-mail/`: the Worker, `wrangler.toml`, README with the owner's steps

## 4. Client preparation

- [x] 4.1 `:sync/request-invite` in every dictionary

## 5. Verify

- [x] 5.1 `clojure -M:test` passes
- [x] 5.2 The flow end to end against a running backend: request, mail logged, page, grant, status, provision
