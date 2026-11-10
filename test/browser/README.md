# Browser suite (Playwright): headless WSL system Chrome, never the shared dev stand
## Running
Needs its own backend on :8301 first:
```sh
npx shadow-cljs compile app   # dev build, never `release`
clojure -X:dictionary-fixture
LEARNING_APP__PORT=8301 LEARNING_APP__DB_PATH=$PWD/test-app.db \
  LEARNING_APP__DICTIONARY_DIR=$PWD/target/test-dictionary clojure -M:dev -m core &
curl -s http://localhost:8301/   # wait for answer
```
```sh
npm run test:browser                            # all
npx playwright test x.spec.js                   # one spec
npx playwright test --project=mobile --no-deps  # one project
npx playwright test --max-failures=1            # use locally while iterating
npx playwright test --debug                     # inspector; trace:
npx playwright show-trace test-results/<dir>/trace.zip
```
Local runs queue machine-wide, one at a time (`machine-lock.js`, #784); a waiting run
says whose run it waits for. Reporter is `line` locally. Traces `retain-on-failure`. CI: four `Browser Tests i/4` shards,
`mobile` run after its shard's `desktop` with `--no-deps` (a sharded run would copy all of
`desktop` into each shard); `Browser Tests` is their aggregate. `fullyParallel`: no test may
rely on another in its file.

## On the release bundle
`*.release.spec.js` (project `release`) are base scenarios that touch only the screen and the network:
no `__metrics`, no `db`, an account from `openWithAccountKey`. They run with the rest on the dev build,
and in the `Release Scenarios` job on the bundle that ships (#701):
```sh
npx shadow-cljs release app   # into resources/public/js/app, which the backend serves
bb scripts/release_renames.clj
npx playwright test --project=release
```
Without `LEARNING_APP__DICTIONARY_DIR`: slow, and `add-form-stability.mobile.spec.js` fails.

## Conventions
- `getByRole`/`getByLabel` + auto-waiting asserts. No `waitForTimeout`, no CSS-class
  locators. Only exception: asserting something never appears, via a named helper
  (`nothingHappensFor`), reason stated.
- Import `test`/`expect` from `./fixtures` (guards Replicant "Triggered a render while
  rendering", #515); self-launched contexts are unwatched.
- Fresh context per test; order-independent.
- Multi-tab: pages in the SAME context (one tab holds `opfs-sahpool`, #351). Playwright
  cannot hide a page: shim `document.hidden` + dispatch `visibilitychange`.
- `boundingBox()` and `window.__metrics()` (dev build only) for geometry/perf specs.

## Seeding from a spec
Prefer the UI. Else seed via dev-build globals: `db` (PouchDB wrapper), never
`db.pouch`/adapters (need `init!` from `main`).
```js
await page.evaluate(async () => {
  const kw = cljs.core.keyword;
  const toClj = (o) => cljs.core.js__GT_clj(o, kw('keywordize-keys'), true);
  await db.insert(db.use('user-db'), toClj({ type: 'review', 'word-id': wordId }));
});
```
- Examples: `adapters.learner.documents.example_doc(wordId, word, collectionId, toClj({value, translation, structure}))`.
- Doc has own `type`, lives in owning db: `user-db` (vocab, review, collection,
  example); `device-db` (lesson).
- Keys go through `clj->couch` (snake_case): `'word-id'` stored as `word_id`.

## Projects
- `desktop`: all specs, 1280x720.
- `mobile`: `*.mobile.spec.js` only (phone-layout specs MUST use that suffix), 390x844,
  `hasTouch`; depends on `desktop`. Per-file UA: `test.use({ userAgent })`.

## Metrics
`__metrics()` (sync), `__metricsReset()`, `__storage()` (promise). Keys kebab-case
(`long-frames`, `layout-shift`); camelCase is silently `undefined`.
- CLS/INP accumulate; reset does not rewind them.
- `web-vitals.cls` ignores shifts near input: assert `layout-shift` or geometry.
- `dictionary.ready-ms` absent (not 0) if dictionary never opens: wait for it.
Out of scope: sync/CouchDB. SW update: route `sw.js` on `context`, not `page`.
SW offline: cut the network with `context.route('**/*', (route) => route.abort('internetdisconnected'))`; `context.setOffline` leaves the worker's own `fetch` online.
