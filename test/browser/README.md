# Browser suite (Playwright)

Headless Playwright specs that replace the ad-hoc CDP checks (#169). They run
inside WSL against the system Google Chrome (`channel: "chrome"`, no browser
download) — never against the shared dev stand or the visible Windows Chrome;
that one remains the CDP skill's territory.

## Running

The suite assumes a backend of its own is already listening on port 8301:

```sh
npx shadow-cljs compile app
clojure -X:dictionary-fixture
LEARNING_APP__PORT=8301 LEARNING_APP__DB_PATH=$PWD/test-app.db \
  LEARNING_APP__DICTIONARY_DIR=$PWD/target/test-dictionary \
  clojure -M:dev -m core &
```

Wait for `curl -s http://localhost:8301/` to answer, then:

```sh
npm run test:browser   # = npx playwright test
```

Config is `playwright.config.js` at the repo root: baseURL
`http://localhost:8301`, `trace: "retain-on-failure"` (traces land in
`test-results/` on failure).

### The fixture dictionary

Word suggestions need a dictionary, and the shipped one is 37 MB. Each test
gets a fresh context, so the SQLite worker would fetch and import all of it
into an empty OPFS before the first suggestion appeared — once per test.
`clojure -X:dictionary-fixture` builds a ~28 KB stand-in from
`test/fixtures/dictionary/lemmas.edn` into `target/test-dictionary`, and
`LEARNING_APP__DICTIONARY_DIR` tells the backend to serve that instead of its
classpath resource. Production leaves the variable unset.

Playwright's `page.route` cannot substitute for this: the dictionary is
fetched by a Web Worker, not the page.

Forget the variable and the suite still runs — the real dictionary contains
the fixture's words too — but slowly, and
`add-form-stability.mobile.spec.js` fails on its match count, which is there
to say so out loud.

## CI

The `Browser Tests` job in `.github/workflows/integration.yml` runs the same
recipe on every relevant pull request: compile the app bundle, boot a backend
on 8301, run the suite against the runner's system Chrome. On failure the job
uploads `test-results/` (traces) and the backend log as an artifact.

## Conventions

- Role- and label-based locators (`getByRole`, `getByLabel`) with auto-waiting
  assertions. No `waitForTimeout`, no CSS-class locators.
- Each test gets a fresh browser context, so client-side storage starts empty
  and tests are order-independent.
- Geometry and performance specs additionally read `boundingBox()` and
  `window.__metrics()`. The latter exists only in a development build
  (`shadow-cljs compile`, never `release`) and its keys keep their
  ClojureScript spelling: `metrics['layout-shift-all']`, not
  `metrics.layoutShiftAll`.

## Projects

`desktop` runs every spec at the default 1280x720. `mobile` runs
`*.mobile.spec.js` at 390x844 with `hasTouch`, which lights up all three arms
of the phone media query (`max-width: 600px`, `pointer: coarse`,
`hover: none`). A spec that needs the phone layout says so in its name.

`mobile` declares `dependencies: ['desktop']` so its measurements are taken on
a machine that is not running other browsers — a long task is main-thread
time, and a shared machine manufactures them. While iterating on a single
spec use `npx playwright test --project=mobile --no-deps`.

Per-file user agents stay `test.use({ userAgent })`, which is why
`dialog-backdrop.spec.js` sets an iPhone UA inside the desktop project: it
wants the install button to render, not a phone viewport.

## Out of scope

- **Service worker / offline** — not asserted here.
- **Sync / CouchDB** — the backend runs alone, no replication target exists.
  "Push to server" therefore reduces to "the word persists across a reload".
- **Sync-menu dialog** — it renders only for a provisioned account (invite
  gate, ADR-0006); a fresh environment has none, so the backdrop-dismiss
  scenario exercises the install guide instead.
