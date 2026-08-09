## 1. Implement

- [x] 1.1 `db-updates` raises with `:status` and `:body` in ex-data
- [x] 1.2 Fan-in catch branches on status: 401/403 → 300s and `:error`, else 5s and `:warn`

## 2. Verify

- [x] 2.1 Tests: backoff decision per error shape; `db-updates` ex-data carries the status end-to-end
