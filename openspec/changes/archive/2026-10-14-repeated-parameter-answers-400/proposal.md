## Why

A repeated `word` or `context` parameter on `/api/examples` threw inside the handler and came back as a 500 whose body was the JVM exception message (#442).

## What Changes

- After the session check, a repeated `word` or `context` is refused with `400` and a fixed message. The provider is not called.
- An exception interceptor stands over every route: an exception answers `500` with an empty body and is logged, never sent.

## Capabilities

### Modified Capabilities
- `examples`: the example endpoint's answer to a repeated parameter and to a handler failure.

## Impact

- `src/backend/core.clj`, the backend handler tests.
