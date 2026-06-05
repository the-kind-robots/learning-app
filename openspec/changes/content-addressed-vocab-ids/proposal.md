## Why

Vocab docs get a random UUID `_id`, and de-duplication is add-time only (`find-duplicate` by normalized value before insert). When two devices independently add the same word, cross-device merge (ADR-0006) unions two UUID-keyed docs with the same value into duplicates — add-time dedup never fires on sync. Per ADR-0008, content-addressing the vocab `_id` by normalized value makes the same word converge to one document, so merge de-duplicates for free.

## What Changes

- **BREAKING**: Vocab document `_id` becomes `"vocab:" + normalize(value)` instead of a random UUID. Same word → same `_id` on every device → one doc with a revision conflict instead of two docs.
- Conflict resolution for vocab SHALL union translations (reuse `merge-translations`), not plain LWW, so a concurrent add with different translations does not drop one side.
- `normalize(value)` becomes a frozen contract: `_id` is permanent, so changing normalization requires a data migration.
- Scope is vocab only. Collections are excluded — their name is mutable (`rename-collection`), so it cannot key the doc.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `data-model`: the vocabulary-document requirement gains a content-addressed `_id` rule, and vocab conflict resolution is specified to union translations.

## Impact

- **Client**: `src/client/domain/vocabulary.cljs` (`new-word` sets a deterministic `_id`), `src/client/adapters/progress_store.cljs` (save path + conflict resolver running `merge-translations`), `src/client/use_cases/vocabulary.cljs` (`find-duplicate` can become get-or-create by `_id`).
- **No migration**: no users yet; the content-addressed `_id` applies to new vocab docs only. Reset dev/test data if needed.
- Enables the clean merge that ADR-0006 relies on. References ADR-0008.
