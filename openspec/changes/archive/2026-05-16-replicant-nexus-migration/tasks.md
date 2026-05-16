## 1. Split the Build

- [x] 1.1 Add `:app` browser module to `shadow-cljs.edn`
- [x] 1.2 Replace CLJS Service Worker build with passive plain JS `resources/public/js/sw.js`
- [x] 1.3 Add `replicant`, `nexus`, and `dataspex` dependencies/assets as needed
- [x] 1.4 Confirm `:app` build compiles without the old client HTTP route/interceptor stack
- [x] 1.5 Restore/verify `:node-test` compile; current check fails because `client/support/db_fixtures.cljs` still requires missing `promesa.core`

## 2. Static App Shell

- [x] 2.1 Serve static app shell from backend default handler with splash and `/js/app/main.js`
- [x] 2.2 Remove htmx script tags and class-tools from the shell; remove them from SW precache list
- [x] 2.3 Code-verify SW does not serve app routes; app navigation falls through to backend/default handler

## 3. Skeleton Main-Thread Boot

- [x] 3.1 Create `src/client/main.cljs`: Replicant render, Nexus dispatch, router, boot sequence
- [x] 3.2 Wire Nexus dispatch loop and shared effects/actions/placeholders in `application.cljs`
- [x] 3.3 Call `db-migrations/ensure-migrated!`, `tasks/start!`, `dbs/init!`, and SW registration from boot sequence
- [x] 3.4 Accepted by review: app shell and first Replicant render are otherwise good

## 4. SQLite Dictionary Worker RPC

- [x] 4.1 Add plain JS Dedicated Worker `resources/public/js/sqlite3-worker.js`
- [x] 4.2 Worker initialises official SQLite WASM with `opfs-sahpool`, imports current dictionary file, opens DB
- [x] 4.3 Add `src/client/dbs.cljs` worker proxy: request-id map, Promise resolution, readiness tracking, error logging
- [x] 4.4 Return no autocomplete completions while worker not ready
- [x] 4.5 Accepted by review: SQLite worker autocomplete is otherwise good; telemetry still tracked separately

## 5. Port /home

- [x] 5.1 Create `:effect/load-home` and home actions: load vocabulary summary, write home slice into state
- [x] 5.2 Wire home hiccup view to Replicant/Nexus actions; replace all `hx-*` attributes
- [x] 5.3 Replace add-word form focus from `hx-on:htmx:after-swap` with Replicant lifecycle hook; preserve desktop-only behaviour
- [x] 5.4 Add SQLite-backed autocomplete completions for the German word input
- [x] 5.5 Accepted by review: `/home` render and add-word/autocomplete are otherwise good

## 6. Port /words

- [x] 6.1 Create words loader/search/update/delete effects and actions
- [x] 6.2 Update words hiccup view: replace `hx-*` attributes with Nexus actions; inline edit dialog replaces server-rendered form
- [x] 6.3 Keep search debounced; reload word list after update/delete
- [x] 6.4 Accepted by review: word list, search, edit, and delete are otherwise good

## 7. Port /lesson

- [x] 7.1 Create lesson loader effect (`lesson/restart!`) and state save action
- [x] 7.2 Create `:action/check-answer`, `:action/next-trial`, `:action/cancel-lesson`, `:action/finish-lesson` effect handlers
- [x] 7.3 Update lesson hiccup view: replace `hx-*` with Nexus actions; focus management via `:replicant/on-mount`; Ctrl+Enter shortcut preserved
- [x] 7.4 Cancel/finish use `rfe/replace-state :page/home` so lesson exit replaces browser history
- [x] 7.5 Accepted by review: lesson flow and exit/back are otherwise good

## 8. Port /token-info (modal)

- [x] 8.1 Create `:action/view-token-info`, `:action/open-modal`, `:action/close-modal`, `:action/save-lesson-word` flow
- [x] 8.2 Token-info dialog rendered inline in lesson page from `:modal/type` / `:modal/data`; uses `<dialog>` + showModal lifecycle hook
- [x] 8.3 Accepted by review: token modal flow is otherwise good

## 9. Delete htmx / old client runtime

- [x] 9.1 Remove `htmx.min.js` and `class-tools.js` from active shell/precache
- [x] 9.2 Remove obsolete helper scripts from active runtime (`sw-loader`, `pwa-install`, `popover`, `virtual-keyboard`, `word-autocomplete`)
- [x] 9.3 Remove client HTTP interceptors/sieppari stubs; keep `reitit.frontend` for browser routing
- [x] 9.4 Zero `hx-*` attributes in `src/client/pages/**` views; install guide ported to Nexus actions
- [x] 9.5 `application.cljs` contains only Nexus registry effects/actions/placeholders, not Ring route handlers

## 10. Polish / Runtime Proof

- [x] 10.1 Add dev action-log inspector through `nexus.action-log/inspect`
- [x] 10.2 Reserve scrollbar gutter in base CSS to prevent layout shift
- [x] 10.3 User review says runtime flows are otherwise good; assistant did not re-run browser proof in this update
- [x] 10.4 No generated-artifact cleanup decision is blocking current closeout
