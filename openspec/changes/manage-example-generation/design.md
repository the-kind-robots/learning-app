## Context

Example fetching runs through local task documents. Fetch scheduling previously delegated to generic task creation, so repeated calls for the same word could create independent task docs unless callers avoided it. Failed fetches also kept retrying with backoff and no user-facing state, so the vocabulary UI could not distinguish "not fetched yet" from "still running" or "failed".

Example documents currently represent fetched sentence data, but the app has no user-owned example flow. Adding that flow touches task scheduling, data model, vocabulary UI, and lesson rendering.

## Goals / Non-Goals

**Goals:**

- Make example fetch task creation idempotent and race-safe across tabs.
- Stop infinite retry loops and preserve failure detail.
- Show users the example lifecycle state for each vocabulary word.
- Let users make or retry examples manually.
- Let users add their own example while preserving fetched examples.
- Let lessons render user examples without word-level structure or popups.

**Non-Goals:**

- No login, account identity, telemetry, or personal-data tracking.
- No multi-example selection UI; lessons choose among usable examples automatically.
- No full example marketplace/library; examples stay local to the user's vocabulary.

## Decisions

### Unique task identity

Use deterministic task ids for work that must be unique by `task-type + normalized data`. The stored id keeps the task type readable and uses an 8-hex-character data hash: `task:<task-type>:<hash32>`. For `example-fetch`, the examples layer passes only `{:word-id <word-id>}`; the task layer owns the stored `_id`.

Rationale: two tabs can ask for an example at the same time; Couch/Pouch conflict is the correct lock. The loser fetches/reuses the existing task instead of creating a second active task.

Alternative considered: query for an existing task before insert. Rejected as the only guard because two tabs can both query empty and then both insert random ids.

### Retry cap and error detail

Cap retryable task failures at 5 attempts. After exhaustion, mark the task as failed with `failure-reason`, `failed-at`, and `last-error`. Manual retry resets attempts and schedules the same deterministic task id again.

Rationale: most fetch failures are not meaningfully helped by hundreds of retries. A small cap keeps local DB state understandable and makes the UI honest.

Alternative considered: 10 attempts. Rejected as too high for paid-token/API failures and validation failures.

### Keep fetched and user examples

Store fetched and user examples as separate example docs with `source`. Missing `source` means source-less fetched data from before this field existed. Lesson creation chooses a random usable example for the word.

Rationale: replacing the fetched example would lose data and force a migration if multiple examples become useful later.

Alternative considered: one canonical example per word. Rejected because it destroys the previous example when the user adds their own.

### User examples are intentionally unstructured

User-created examples save text and translation exactly as entered with `source "user"` and no required `structure`. No structure task is scheduled for user examples. Lessons render user examples as plain sentence/translation with no word popups.

Rationale: user examples are controlled input. Running structure on them can surprise the user by changing interpretation or adding affordances they did not ask for.

### Example management uses resource routes

Expose example management through resource-oriented routes instead of RPC-style action routes:

- `GET /words/:word-id/examples` returns examples for the word.
- `POST /words/:word-id/examples` adds a user-owned example under the word, returns the same panel representation for HTMX replacement, and includes `Location: /examples/:example-id`.
- `GET /words/:word-id/example-fetch` returns the word's example fetch state.
- `PUT /words/:word-id/example-fetch` asks the app to fetch an example idempotently without a request body and returns the same fetch-state representation as `GET`. If an example is already ready, it returns the ready representation without scheduling replacement work.
- `PATCH /examples/:example-id` updates a user-owned example.
- `DELETE /examples/:example-id` deletes one example without scheduling replacement work.

Rationale: examples are child resources of words, and example fetch state is a single known resource per word. Keeping identity in the path makes the UI and API read as resource representations, not verbs.

### Fetched examples require structure

Fetched examples (`source "fetched"` or missing `source`) use a tolerant-reader schema. The client stores parseable fetched example maps and preserves unknown fields so newer/richer backend payloads are not lost. Before lesson use, fetched examples must still have the critical fields this client can render: nonblank sentence, nonblank translation, and valid sentence structure. If an existing fetched example lacks those fields or has an outdated structure shape, the app treats it as unusable instead of showing a broken structured trial.

Rationale: fetched examples are the only source of word popups. Missing structure on fetched docs means source-less, outdated, or broken data, not intentional plain rendering.

### Delete does not auto-refetch

Deleting an example removes that example and leaves the word in `no example` state unless another usable example exists. It does not enqueue replacement work.

Rationale: delete is user control. Auto-refetch after delete creates a loop.

## Risks / Trade-offs

- Race handling differs by task type -> keep unique-task APIs small and explicit until another task type needs them.
- Random example choice reduces exact lesson reproducibility but gives useful variety.
- User examples have fewer lesson affordances -> render plain text and reserve popups for structured fetched examples.
- Old random example-fetch tasks may already exist -> migration/dead-letter cleanup should collapse or disable duplicates before new deterministic tasks take over.

## Migration Plan

- Add deterministic ids for new example-fetch tasks.
- Add a local migration that groups existing active example-fetch tasks by word and keeps at most one active task.
- Keep source-less fetched example docs unchanged; read paths treat missing `source` as fetched.
- Store new user examples with `source "user"` and `modified-at`; do not require or generate structure.
- Existing failed tasks without `last-error` remain valid; new failures record richer details.

## Open Questions

- Whether the first UI pass should include edit/replace for fetched examples, or only add user example + retry fetch.
- Whether user examples should require Russian translation immediately or allow translation to be added later.
