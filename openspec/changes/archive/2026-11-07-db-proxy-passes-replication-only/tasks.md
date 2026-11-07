## 1. Proxy rules

- [x] 1.1 Production nginx: replication allow-list map, feed and `_local` length rules, 403 before `auth_request`
- [x] 1.2 Production nginx: 415 on `Content-Encoding`, explicit 1 MB body cap, `limit_conn` per address answered 429, shorter `/db/` timeouts
- [x] 1.3 Production nginx: throttled answers left out of the access log
- [x] 1.4 Development nginx carries the same `/db/` rules

## 2. Staging smoke

- [x] 2.1 Smoke seeds an account and asserts every replication request shape is forwarded
- [x] 2.2 Smoke asserts the refused shapes: unused endpoints, live feeds, gzip body, oversized or unsized checkpoint
- [x] 2.3 Smoke asserts the connection limit answers 429

## 3. Verification

- [x] 3.1 `infra/staging/run.sh` green (CI "Staging smoke" on the PR)
- [x] 3.2 A real PouchDB push-and-pull pass, empty device and a heavy learner's data, through the proxy rules
- [x] 3.3 The development config passes `nginx -t`
