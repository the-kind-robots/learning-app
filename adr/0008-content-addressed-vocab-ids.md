# 0008. Content-addressed vocab document ids

- Status: proposed
- Date: 2026-06-04

## Context

Vocab docs get a random UUID `_id` — `new-word` sets no `_id`, PouchDB generates one. Dedup is add-time only: `add!` queries `find-duplicate` by normalized value, merges translations, before insert. Works within one device.

Cross-device merge (ADR-0006) unions two local datasets via replication. Two devices each added the same word → two UUID-keyed docs, same value → replication keeps both → duplicate-by-value. Add-time dedup never fires (runs on add, not on sync).

Word value is immutable — editing is restricted to translation (GH-96). So a value-derived id never needs to change.

## Decision

Content-address vocab docs: `_id = "vocab:" + normalize(value)`, not a random UUID. Same word on any device → same `_id` → replication yields one doc with a revision conflict, not two docs. Cross-device dedup is then free — no post-merge pass for words.

Conflict resolution must **union translations**, not plain LWW: two devices adding the same word with different translations conflict on one doc, and LWW would drop the loser's translation. Reuse the existing `merge-translations` (today add-time) in the conflict resolver.

`normalize` becomes a frozen contract: `_id` is permanent, so changing normalization (articles, case, umlauts) remaps the same word to a new `_id` and re-duplicates. Changing it = a data migration.

Scope: vocab only. Collections keep surrogate ids — their name is mutable (`rename-collection`), so it cannot key the doc; collection merge stays a post-merge concern.

## Consequences

- Cross-device merge dedups vocab for free; the post-merge dedup pass (ADR-0006) drops for words, leaving only translation-union on conflict.
- Homographs (788, e.g. "Abort") collapse to one doc — aligned with the merge-as-one-lemma policy.
- `normalize` is now an immutable contract; any change needs a migration of all vocab `_id`s.
- Translation merge moves from add-time row-dedup to conflict-time field-merge — cleaner (one doc, merge fields), but the resolver must run `merge-translations`, not LWW.
- Migration: existing UUID-keyed vocab + reviews referencing them by `:word-id` must be rekeyed (new `_id`, repoint review `:word-id`). New installs unaffected.
- Add-time `find-duplicate` can simplify to get-or-create by `_id`, as long as value normalization stays identical to the id scheme.
- Collections excluded (mutable name); their cross-device dedup is unsolved here.
- Implementation — id scheme, conflict resolver with `merge-translations`, migration of existing docs + review refs — deferred to an OpenSpec change referencing this ADR.
