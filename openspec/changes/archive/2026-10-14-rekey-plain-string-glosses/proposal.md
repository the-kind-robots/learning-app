## Why

The startup re-key skipped rows whose glosses column holds a plain string instead of a JSON array, so most older examples stayed unreachable (#565).

## What Changes

- A plain-string glosses value is read as one gloss, the way a request's `translation` string is. The row and a request for the same word land on one key.
- A row whose glosses are neither a JSON array nor a non-blank string is still skipped and left waiting.

## Capabilities

### Modified Capabilities
- `example-cache`: the re-key of earlier rows.

## Impact

- `src/backend/examples/cache.clj`, `src/backend/examples.clj`, the cache tests.
