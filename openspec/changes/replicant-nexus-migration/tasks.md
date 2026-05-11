## 1. Split the Build

- [x] 1.1 Add `:app` browser module and `:dictionary-worker` web-worker module to `shadow-cljs.edn`
- [x] 1.2 Strip `sw.cljs` to passive cache only: remove all app-logic requires (`application`, `dictionary*`, `tasks`, `lesson`, `vocabulary`, `db-migrations`)
- [x] 1.3 Add `replicant` and `nexus` to `deps.edn` / `package.json` as needed
- [x] 1.4 Confirm all three modules (`:app`, `:sw`, `:dictionary-worker`) compile without errors

## 2. Static App Shell

- [x] 2.1 Create `resources/public/index.html` app shell with `<div id="app">` and a loading splash
- [x] 2.2 Remove htmx script tags and class-tools from the shell; remove them from SW precache list
- [ ] 2.3 Verify SW serves `index.html` for all navigation requests (network-first fallback)

## 3. Skeleton Main-Thread Boot

- [x] 3.1 Create `src/client/main.cljs`: mount Replicant on `#app` with a placeholder hiccup on `DOMContentLoaded`
- [x] 3.2 Wire Nexus dispatch loop with a no-op action to confirm the event pipeline compiles
- [x] 3.3 Call `db-migrations/ensure-migrated!` and `tasks/start!` from boot sequence
- [ ] 3.4 Confirm app shell loads and placeholder renders in browser

## 4. SQLite Dictionary Worker RPC

- [ ] 4.1 Create `:dictionary-worker` module entry: `src/client/dictionary_worker.cljs`; move `dictionary_sqlite.cljs` init + suggest logic into it
- [ ] 4.2 Expose `suggest` over postMessage: worker listens for `{:type :suggest :id rid :input s}`, posts back `{:type :result :id rid :data result}`
- [ ] 4.3 Create `src/client/dictionary_worker_rpc.cljs` on main thread: spawns worker, wraps calls in promise-based request-id map, exposes `suggest` returning a Promise
- [ ] 4.4 Return `{:suggestions [] :prefill nil}` synchronously when worker not yet ready
- [ ] 4.5 Verify `suggest` works end-to-end from browser console before any UI integration

## 5. Port /home

- [x] 5.1 Create `:action/navigate-home` effect handler: reads PouchDB vocabulary summary, writes `{:page :home :data {...}}` into state atom
- [x] 5.2 Wire the home hiccup view (`views/home`) to read from `:data` in state; replace all `hx-*` attributes with Nexus action data (`:on {:click [...]}}`)
- [x] 5.3 Replace add-word form focus from `hx-on:htmx:after-swap` with a Replicant lifecycle hook; preserve desktop-only behaviour
- [x] 5.4 Confirm `/home` renders correctly and add-word form works end-to-end

## 6. Port /words

- [x] 6.1 Create `:action/navigate-words` loader effect; create `:action/delete-word`, `:action/set-words-page` (search+reload, load-all — no offset pagination; revisit if vocab > ~500 words), `:action/open-word-edit`, `:action/close-word-edit`, `:action/update-word` effect handlers (`:action/add-word` already existed)
- [x] 6.2 Update `views/vocabulary` hiccup: replace `hx-*` attributes with Nexus actions; inline edit dialog replaces server-rendered form
- [ ] 6.3 Confirm word list, search, edit, and delete work end-to-end

## 7. Port /lesson

- [x] 7.1 Create `:action/navigate-lesson` loader effect (`lesson/restart!`); atomic swap with lesson-state or empty? flag
- [x] 7.2 Create `:action/check-answer`, `:action/next-trial`, `:action/cancel-lesson`, `:action/finish-lesson` effect handlers
- [x] 7.3 Update `views/lesson` hiccup: replace hx-* with Nexus actions; focus management via `:replicant/on-mount`; Ctrl+Enter shortcut preserved
- [x] 7.4 cancel/finish use `go-home!` which calls `history.replaceState` (lesson entry replaced, not pushed)
- [ ] 7.5 Confirm full lesson flow: start → trials → correct/incorrect → completion → exit

## 8. Port /token-info (modal)

- [x] 8.1 Create `:action/open-token-info`, `:action/close-modal`, `:action/token-add` effect handlers; write to `:modal` slice; auto-close `:known-with-translation` after 900ms
- [x] 8.2 Token-info dialog rendered inline in lesson page from `state :modal :data`; uses `<dialog>` + showModal lifecycle hook
- [ ] 8.3 Confirm modal opens, closes, and does not disturb lesson state

## 9. Delete htmx

- [x] 9.1 Remove `htmx.min.js` and `class-tools.js` script tags from `index.html` (already clean from Section 2)
- [x] 9.2 Remove htmx entries from SW precache manifest (already clean from Section 1)
- [x] 9.3 Remove `reitit` and `sieppari` from `shadow-cljs.edn`; delete `src/client/reitit/` stub files
- [x] 9.4 Zero `hx-*` attributes in `views/*`; `app_install_guide` ported to Nexus actions
- [x] 9.5 `application.cljs` contains only Nexus effect registry (ring/reitit routes never existed there)

## 10. Rollout Flag Cleanup

- [ ] 10.1 Verify SQLite worker `suggest` is stable across page reloads and offline/online cycles
- [ ] 10.2 Remove PouchDB dictionary fallback path from suggest callers
- [ ] 10.3 Remove rollout flag from codebase
