## 1. Catch it (#515)

- [x] 1.1 `test/browser/fixtures.js` fails a test when Replicant reports "Triggered a render while rendering"; every spec imports `test` from it
- [x] 1.2 The suite fails on master with it

## 2. Remove the write

- [x] 2.1 The status region carries `id="app-status"`; the announcing action passes that id to `:effect/announce`, which looks it up at announce time
- [x] 2.2 `:effect/hold-status-region` and `:app/status-region` are gone
- [x] 2.3 The announce specs, the unit tests and the browser suite pass
