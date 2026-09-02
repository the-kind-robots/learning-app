# Change: Add base German dictionary with CouchDB sync and autocomplete

## Why
Users should be able to add words quickly with canonical spelling, autocomplete, and error checking. A predefined German dictionary enables that while keeping the app offline-first and low-friction.

## What Changes
- Add a predefined German CEFR A1-C2 dictionary stored in a public read-only CouchDB database and synced to clients via PouchDB replication.
- Split storage into `user-db` (vocab/reviews, local only), `device-db` (tasks/examples, local only), and `dictionary-db` (synced from server).
- Add a loader script that downloads source data and outputs JSONL dictionary entries and forms with metadata plus a sizing manifest.
- Add a client dictionary loader that checks for initial load and triggers background sync if not loaded.
- Update task documents to use `data` payloads (remove top-level `word-id`).

## Impact
- Affected specs: `specs/data-model/spec.md`, `specs/task-runner/spec.md`, new `specs/dictionary/spec.md`.
- Affected code: client db usage, task runner, dictionary sync, autocomplete/add flow, ingestion tooling.
- Notes: Dictionary is public read-only via CouchDB, no authentication required for sync. CouchDB sync handles interruptions and retries natively.

## Links
- Issue: https://github.com/the-kind-robots/learning-app/issues/63
