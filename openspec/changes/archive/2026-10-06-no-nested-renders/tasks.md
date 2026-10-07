## 1. Catch it (#515)

- [x] 1.1 `test/browser/fixtures.js` fails a test when Replicant reports "Triggered a render while rendering"; every spec imports `test` from it
- [x] 1.2 The suite fails on master with it
- [x] 1.3 `delayed-frames.spec.js`: with every animation frame a second late, home and the lesson stay on display after memory loads; fails on master

## 2. Remove the write

- [x] 2.1 The status region and its id live in `application.shell`; the delete control puts it into its payload, the deletion chain passes it to `:effect/announce`, which looks the region up at announce time
- [x] 2.2 `:effect/hold-status-region` and `:app/status-region` are gone
- [x] 2.3 The announce specs, the unit tests and the browser suite pass
