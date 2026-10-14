## Why

The example cache key hashes the system prompt and the model list. Any prompt edit or model change makes every stored example unreachable, so good examples are regenerated and paid for again (#565).

## What Changes

- The key is the subject (word, glosses, context) and the dictionary's reading of the word. The prompt and the models leave it.
- `EXAMPLE_CACHE_VERSION` joins the key. Unset is the normal state; setting or changing it is the deliberate full regeneration.
- At startup, rows stored under the old keys are re-keyed. Two rows landing on one key keep the older one.

## Capabilities

### Modified Capabilities
- `example-cache`: the key's material, the two scenarios that made a prompt or model change a miss, and the re-keying of earlier rows.

## Impact

- `src/backend/examples.clj`, `src/backend/examples/cache.clj`, `src/backend/core.clj`, a SQL migration for the re-key marker, `docs/ops/server-configuration.md`, the cache tests.
