## 1. Key

- [x] 1.1 The key drops the prompt and the models and takes `EXAMPLE_CACHE_VERSION`

## 2. Re-key

- [x] 2.1 A migration marks existing rows as waiting for a new key
- [x] 2.2 A startup step re-keys them in batches, the older of two rows kept

## 3. Tests and docs

- [x] 3.1 Unit tests of the key and the re-key
- [x] 3.2 `docs/ops/server-configuration.md` names `EXAMPLE_CACHE_VERSION`
