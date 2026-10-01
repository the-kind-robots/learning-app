## Why

An address such as `/home?x=%` made the router throw `URIError` while it read the address, and the app's start failed. Before #515's splash change the shell then showed «Загружаем...» for good; with it, a bare bar with no screen and no working navigation.

## What Changes

- Before the router starts, a query that is not valid percent-encoding is dropped from the address. The router then starts on the path as usual.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-navigation`: adds a requirement that a malformed query does not stop the app.

## Impact

- `src/client/main.cljs` (`:app/router` start).
- `test/browser/malformed-query.spec.js`.
