## 1. Replication primitive

- [x] 1.1 One-shot `sync-once!` in `sync.cljs` — `db.sync(remote, {live: false})`, resolves vocab conflicts on completion, never rejects
- [x] 1.2 Remove the permanent `{:live true :retry true}` replication and `stop!` from `sync/start!`

## 2. Push on write

- [x] 2.1 Throttled (3s, leading-edge) pass driven by the local `user-db` `.changes({since: "now", live: true})` feed, gated on `navigator.onLine`

## 3. Pull triggers

- [x] 3.1 Expose the pass as `:sync/pull!` through `:capabilities/sync`; `:effect/sync-pull` calls it and dispatches `:action/reload-page` on completion
- [x] 3.2 Dispatch `:effect/sync-pull` from the words, collections and lesson route controllers
- [x] 3.3 `:app/sync` component: `online` listener dispatching `:effect/sync-pull` (unconditional — also flushes offline writes)
- [x] 3.4 Lesson pulls on entry too (reload stays a no-op there); home does not pull

## 4. State-driven reload

- [x] 4.1 Page show actions write `:page/current` and `:page/load` (`[:effect/load-words]`, `[:effect/load-collections]`, nil for home/lesson)
- [x] 4.2 `:action/reload-page` re-dispatches the effect in `:page/load`; no-op when nil
- [x] 4.3 Top-level render strips nil children (Replicant issue #70) so conditional shell fragments can't freeze the render loop

## 5. Verify

- [x] 5.1 `npx shadow-cljs compile app` clean and node tests green
- [ ] 5.2 Two-browser check: write on device A → pass runs, no held feed; B pulls A's change on words/collections entry
- [ ] 5.3 Data-page entry renders local instantly, fresh data appears after the pull
- [ ] 5.4 `online` event triggers a pass; reload only on data pages
