## Why

Users want to study context-specific vocabulary (e.g. "Driving School" today, "Travel" tomorrow). Today all words share one flat list and AI examples use a neutral prompt regardless of context. Collections let users group words by topic so AI examples become domain-specific, making practice more relevant.

## What Changes

- Add a **collections** data model: named collections stored in PouchDB (device DB), with an "Все слова" (All Words) default that is undeletable and always contains all words.
- Add a **many-to-many word↔collection** relationship: a word can belong to multiple collections; adding a word attaches it to the active collection and All Words.
- Add **active collection** persistence in localStorage: survives restarts, defaults to All Words.
- Add **Collections Grid UI** on the home screen: 2×2 grid icon top-right + active collection name; tap opens a grid of collection cards (Safari Tab Grid metaphor); tap card switches active collection; dashed "+" card to create; long-press → Rename / Delete.
- **BREAKING — Examples schema**: example documents gain a `collection-id` field. The lookup key becomes `(word-id, collection-id)` instead of `word-id` alone. Each collection gets its own AI examples per word. All Words collection uses a neutral prompt (no injection). This is handled in the same change (no separate change needed — the examples schema migration is scoped here).
- Backend `/api/examples` endpoint gains an optional `collection` query parameter injected into the AI prompt.

## Capabilities

### New Capabilities

- `collections-data-model`: PouchDB document shapes for collection and word-collection-membership; All Words bootstrap; active-collection persistence in localStorage.
- `collections-navigation`: Home screen Collections Grid UI — grid icon, tab-grid overlay, card render, create/rename/delete flows, active-collection switching.
- `examples-schema`: Updated example document shape with `collection-id`; updated lookup key; per-collection AI example generation; backend prompt injection for non-All-Words collections.

### Modified Capabilities

- `examples`: Example fetch tasks now carry `collection-id`; `request!` signature gains collection-id arg; `find` / `list` queries gain collection-id filter.
- `data-model`: Example document shape gains `collection-id` field (**BREAKING** existing stored docs have no `collection-id` — treated as All Words collection on read via default fallback).

## Impact

- `src/client/adapters/examples.cljs` — example doc shape, fetch task payload, find/list queries
- `src/client/ports/examples.cljs` — expose collection-id in port API
- `src/client/adapters/progress-store.cljs` — delete-word! cleans up examples by word-id+collection-id
- `src/client/db/pouch.cljs` — new doc-type `"collection"` → `:device/db`, `"word-collection"` → `:device/db`
- `src/client/domain/vocabulary.cljs` — new-word gains active-collection context
- `src/client/use_cases/vocabulary.cljs` — add! attaches word to active collection
- `src/client/pages/home/` — new grid icon, collections overlay page/view/actions/effects
- `src/client/main.cljs` — port/collections added to capabilities
- `src/client/application.cljs` — new :page/collections route
- `src/backend/core.clj` — `/api/examples` gains `collection` param; forwards to examples/generate-one!
- `src/backend/examples.clj` (or provider) — prompt injection for collection name
- `initial-setup.sql` — no server-side schema change needed (examples stored client-side only)
