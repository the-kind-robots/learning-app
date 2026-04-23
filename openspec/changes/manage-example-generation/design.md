## Context

Example generation runs through local task documents. Fetch scheduling previously delegated to generic task creation, so repeated calls for the same word could create independent task docs unless callers avoided it. Failed generation also kept retrying with backoff and no user-facing state, so the vocabulary UI could not distinguish "not generated yet" from "still running" or "failed".

Example documents currently represent generated sentence data, but the app has no user-owned example flow. Adding that flow touches task scheduling, data model, vocabulary UI, and lesson rendering.

## Goals / Non-Goals

**Goals:**

- Make example fetch task creation idempotent and race-safe across tabs.
- Stop infinite retry loops and preserve failure detail.
- Show users the example lifecycle state for each vocabulary word.
- Let users generate/retry examples manually.
- Let users add their own example while preserving AI examples.
- Let lessons render user examples without word-level structure or popups.

**Non-Goals:**

- No login, account identity, telemetry, or personal-data tracking.
- No multi-example selection UI beyond choosing the preferred example.
- No full example marketplace/library; examples stay local to the user's vocabulary.

## Decisions

### Unique task identity

Use deterministic task ids for work that must be unique by `task-type + normalized data`. The stored id keeps the task type readable and uses an 8-hex-character data hash: `task:<task-type>:<hash32>`. For `example-fetch`, the examples layer passes only `{:word-id <word-id>}`; the task layer owns the stored `_id`.

Rationale: two tabs can click "Generate" at the same time; Couch/Pouch conflict is the correct lock. The loser fetches/reuses the existing task instead of creating a second active task.

Alternative considered: query for an existing task before insert. Rejected as the only guard because two tabs can both query empty and then both insert random ids.

### Retry cap and error detail

Cap retryable task failures at 5 attempts. After exhaustion, mark the task as failed with `failure-reason`, `failed-at`, and `last-error`. Manual retry resets attempts and schedules the same deterministic task id again.

Rationale: most generation failures are not meaningfully helped by hundreds of retries. A small cap keeps local DB state understandable and makes the UI honest.

Alternative considered: 10 attempts. Rejected as too high for paid-token/API failures and validation failures.

### Keep AI and user examples

Store AI and user examples as separate example docs with `source` and `preferred?`. Lesson rendering uses the preferred example for the word. Adding a user example makes it preferred, but does not delete the AI example.

Rationale: replacing the AI example would lose data and force a migration if multiple examples become useful later.

Alternative considered: one canonical example per word. Rejected because it destroys the previous example when the user adds their own.

### User examples are intentionally unstructured

User-created examples save text and translation exactly as entered with `structure-status "absent"` and no `structure`. No AI structure task is scheduled for user examples. Lessons render sentence/translation, but word popups only appear for examples that already have ready structure.

Rationale: user examples are controlled input. Running AI structure on them can surprise the user by changing interpretation or adding affordances they did not ask for.

### Delete does not auto-regenerate

Deleting an example removes or unprefers that example and leaves the word in `no example` state unless another preferred example exists. It does not enqueue generation.

Rationale: delete is user control. Auto-regeneration after delete creates a loop goblin.

## Risks / Trade-offs

- Race handling differs by task type -> keep unique-task APIs small and explicit until another task type needs them.
- Multiple examples can make "the example" ambiguous -> require exactly one preferred example per word in normal flows.
- User examples have fewer lesson affordances -> render plain text and reserve popups for structured AI examples.
- Old random example-fetch tasks may already exist -> migration/dead-letter cleanup should collapse or disable duplicates before new deterministic tasks take over.

## Migration Plan

- Add deterministic ids for new example-fetch tasks.
- Add a local migration that groups active legacy example-fetch tasks by word and keeps at most one active task.
- Extend existing example docs with `source "ai"`, `preferred? true` when no other preferred example exists, and `structure-status "ready"` when `structure` is present.
- Store new user examples with `source "user"`, `preferred? true`, and `structure-status "absent"`.
- Existing failed tasks without `last-error` remain valid; new failures record richer details.

## Open Questions

- Whether the first UI pass should include edit/replace for AI examples, or only add user example + retry generation.
- Whether user examples should require Russian translation immediately or allow translation to be added later.
