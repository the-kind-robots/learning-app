## Context

In-force ADRs: ADR-0008 (this change), with ADR-0006 (provisioning) and ADR-0007 (sync triggers) — none superseded. Current: `new-word` sets no `_id` so PouchDB assigns a UUID; `add!` dedups add-time via `find-duplicate` (normalized value) and `merge-translations`. Word value is immutable — editing is restricted to translation (GH-96), which makes a value-derived id safe.

## Goals / Non-Goals

**Goals:**
- Same word → same `_id` → free cross-device de-duplication via replication.
- No lost translations when the same word is added concurrently.

**Non-Goals:**
- Collections de-duplication (mutable name) — out of scope.
- The merge/replication mechanics themselves (ADR-0006/0007).

## Decisions

- **`_id = "vocab:" + normalize(value)`.** Deterministic, namespaced. Rationale: replication collapses same-word docs into one conflicted doc instead of duplicates. Safe because `value` is immutable. Alternative (keep UUID + post-merge dedup pass) rejected — fragile and recurring.
- **Conflict resolver unions translations.** Reuse `merge-translations` (today add-time) inside the conflict handler. Rationale: plain LWW would drop the loser's translations. The work shifts from row-dedup to field-merge — one doc, merge fields.
- **`normalize` is frozen.** Once it keys `_id`, any change remaps words and re-duplicates; treat as a contract, change only via migration.
- **`find-duplicate` simplifies** to get-or-create by `_id` (normalization must match the id scheme exactly).

## Risks / Trade-offs

- Normalization change later → mass id migration → freeze and document it; cover the current rules with tests.
- Homographs collapse to one doc — intended, matches the merge-as-one-lemma policy (788 homographs).

## Migration Plan

No migration. There are no users yet; the content-addressed `_id` applies to all new vocab docs. Reset any dev/test data instead of rewriting ids.

## Open Questions

- Where the conflict resolver lives relative to the sync-triggers change (ADR-0007) — it must run after a merge pass; coordinate ordering when both land.
