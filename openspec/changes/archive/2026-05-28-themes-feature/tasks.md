## 1. Examples Schema — add collection-id

- [x] 1.1 Update `adapters.examples/save-example!` to accept and store `collection-id` in the example doc
- [x] 1.2 Update `adapters.examples/find` to query by `(word-id, collection-id)` — All Words is a union across collection-ids; named collections are strict
- [x] 1.3 Update `adapters.examples/list` to filter by `collection-id` with the same union semantics on All Words
- [x] 1.4 Update `adapters.examples/request!` and the `"example-fetch"` task handler to carry `collection-id` and `collection-name` in the task payload
- [x] 1.5 Update `ports.examples/start!` to expose `collection-id` params in find/list/request! port functions
- [x] 1.6 Update backend `core.clj` `/api/examples` to accept optional `context` query param and forward to `examples/generate-one!`
- [x] 1.7 Update backend `examples.clj` to inject context into AI prompt when present

## 2. Collections Data Model — PouchDB layer

- [x] 2.1 Add `"collection"` and `"word-collection"` to `db.pouch/doc-type->db` mapping (`:device/db` for both)
- [x] 2.2 Create `adapters.collections` namespace with: `create-collection!`, `rename-collection!`, `delete-collection!`, `list-collections`, `add-word-to-collection!`, `remove-word-from-collection!`, `word-collections`, `collection-word-count`
- [x] 2.3 Create `ports.collections` namespace wrapping `adapters.collections` with clock dependency
- [x] 2.4 Add `"all-words"` constant and virtual-collection helpers in `adapters.collections`: always inject as first item in list, block deletion
- [x] 2.5 Add `ports.collections` to `:app/capabilities` in `main.cljs`

## 3. Active Collection Persistence

- [x] 3.1 Create `adapters.active-collection` namespace: `active-collection-id`, `active-collection-name`, `set-active-collection!`, `validate-and-restore!`; defaults to `"all-words"` when missing/invalid
- [x] 3.2 Merge active-collection into `ports.collections`; exposed as `:collections/active-id`, `:collections/active-name`, `:collections/set-active!`, `:collections/validate!`

## 4. Vocabulary Integration — attach word to active collection

- [x] 4.1 Update `use-cases.vocabulary/add!` to attach the new word to the active collection (named only) and queue a context-specific example fetch
- [x] 4.2 In the duplicate-word branch, queue a context-specific example fetch when adding into a named collection that has no example for that `(word-id, collection-id)` pair

## 5. Collections Grid UI — navigation and page structure

- [x] 5.1 Add `:page/collections` route in `application.cljs` with controller `[:effect/load-collections]`
- [x] 5.2 Create `pages.collections.actions` namespace with show/select/create/rename/confirm-delete/cancel-delete actions
- [x] 5.3 Create `pages.collections.effects` namespace with load/add/rename/delete/switch effects
- [x] 5.4 Create `pages.collections.view` namespace rendering the grid layout with cards, dashed create card, and active-collection highlight
- [x] 5.5 Require new namespaces in `main.cljs`

## 6. Home Screen — collections icon and active collection label

- [x] 6.1 Add collections grid icon button to `pages.home.view/page` header
- [x] 6.2 Add `action/go-to-collections` action (via `<a href="/collections">`)
- [x] 6.3 Update `effect/load-home` to load and store active collection name for display

## 7. Collection card — context menu (long-press)

- [x] 7.1 Long-press detection in `pages.collections.view` using pointer events / contextmenu
- [x] 7.2 Render rename/delete inline editing menu when long-press fires on a named collection card
- [x] 7.3 Render confirmation dialog for delete with Russian text "Удалить набор «X»? Слова останутся в «Все слова»."

## 8. Verification

- [x] 8.1 `npx shadow-cljs compile app` passes in worktree
- [x] 8.2 Manual end-to-end: examples generated with collection context; backend prompt injection verified by running app
- [x] 8.3 Manual end-to-end: example docs stored with `:collection-id` for named collections, without it for All Words; cross-collection re-fetch confirmed
