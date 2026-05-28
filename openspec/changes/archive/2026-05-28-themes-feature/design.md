## Context

The app stores all user data (words, reviews, examples, lesson state, tasks) in PouchDB on the client, split across `user-db` (vocab, reviews) and `device-db` (examples, tasks, lessons). The backend exposes `/api/examples` which calls an AI provider and returns a generated example sentence.

Currently there is no concept of collections. All words are in one flat list, and AI examples use a neutral prompt. The feature adds collections as a lightweight context layer on top of the existing model.

Existing ADRs 0001–0005 cover dictionary storage, worker architecture, and the system/component graph. None constrain collection storage choices directly.

## Goals / Non-Goals

**Goals:**
- Collection documents stored in `device-db` (no cross-device sync needed — collections are device-local organization, not user vocabulary)
- Many-to-many word↔collection membership via `word-collection` join documents
- "Все слова" (All Words) as an undeletable virtual collection — not a stored document, represented as `collection-id: "all-words"`
- Active collection persisted in `localStorage` (survives restart, device-local, zero schema cost)
- Collection-scoped AI examples: example docs gain `collection-id`; lookup key = `(word-id, collection-id)`
- Backend collection injection: collection name forwarded to AI prompt via query param; All Words → no injection
- Minimal UI: grid icon on home header, full-screen grid overlay as a new route (`:page/collections`)
- Migration: existing example docs (no `collection-id`) read as belonging to All Words via fallback

**Non-Goals:**
- Cross-device collection sync (collections are organizational, not vocabulary data)
- Animated zoom-out/zoom-in (crossfade is sufficient for v1)
- Collection-level SRS progress (reviews stay global by `word-id`)
- Server-side `examples` table changes (client PouchDB only)

## Decisions

### D1: Collection storage in device-db (not user-db)

Alternatives: user-db (synced), server-side SQLite, IndexedDB directly.

Chosen: `device-db` PouchDB. Collections are organizational preferences, not vocabulary. They don't need cross-device sync. Reusing the existing PouchDB layer avoids new dependencies. New doc types: `"collection"` and `"word-collection"`.

### D2: All Words as a constant, not a stored document

`"all-words"` is a hardcoded sentinel `collection-id`. No document is created. The port/collections layer returns it as the first item in every collection list, always, and blocks deletion. This avoids bootstrap/migration complexity.

### D3: Active collection in localStorage

Alternatives: device-db doc, app-state only (lost on restart).

Chosen: `localStorage` key `"active-collection-id"`. Synchronous, zero async, survives restarts. Active collection is set on mount, updated on switch. Defaults to `"all-words"` if missing or invalid.

### D4: Example doc gains collection-id field; old docs fallback to "all-words"

Alternatives: migrate all existing example docs on first load; delete and re-fetch.

Chosen: read-time fallback. `adapters.examples/find` queries by `(word-id, collection-id)`. If `collection-id = "all-words"` and no doc found, also check docs with no `collection-id` field (legacy). This avoids a migration pass. New examples are always written with `collection-id`.

### D5: Collections Grid as a new route (:page/collections)

Alternatives: modal overlay, drawer, inline expansion.

Chosen: full-screen route for composability with back-navigation. The Replicant/Nexus router already handles page transitions. A new `:page/collections` route follows the existing pattern with load/show actions.

### D6: Word-collection membership on word creation

When `add!` succeeds, `use-cases.vocabulary/add!` calls collections port to add membership: active collection + All Words (if active ≠ all-words, two docs; if active = all-words, one implicit — no doc needed since All Words is virtual).

Actually All Words is virtual — no `word-collection` docs needed for it. Only named collections need `word-collection` docs. On word creation: if active collection ≠ all-words, create one `word-collection` doc `{type "word-collection" word-id … collection-id …}`.

## Risks / Trade-offs

- **[Risk] Example doc migration** → Mitigation: read-time fallback (D4) avoids upfront cost. First fetch for a new collection will re-request AI — acceptable.
- **[Risk] Stale active-collection-id in localStorage after collection deletion** → Mitigation: collections port validates active collection on load; resets to all-words if missing.
- **[Risk] Many word-collection docs at scale** → Low risk for a vocabulary app (hundreds of words, not millions). No index needed for v1.

## Migration Plan

1. Deploy: no server changes. Client code deployed via SW update.
2. On first use: existing examples work via fallback (D4). Active collection defaults to all-words.
3. No rollback needed — additive only. Old client ignores unknown doc types.

## Open Questions

- Long-press on mobile: use `pointerdown` + timer vs. `contextmenu` event. Decision deferred to implementation; `contextmenu` is simplest cross-platform.
