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

### Several tabs at once

`dictionary-focus.spec.js` opens more than one page in the same context on
purpose: only one tab can hold `opfs-sahpool`, and which one it is follows
visibility (#351, ADR-0012). Pages of *different* contexts share nothing —
separate storage, separate lock — so they prove nothing here.

Playwright cannot hide a page: every page reports `visible`,
`bringToFront()` does not change that, and
`Emulation.setPageVisibilityOverride` is gone from the protocol. All three
were checked against this Chrome. So that spec shims `document.hidden` and
dispatches the real `visibilitychange`, which drives the whole app path —
page listener, worker message, lock, pool, database. It does not reproduce
Android freezing a backgrounded tab, which is the reason the rule exists;
nothing here covers that.

## CI

The `Browser Tests` job in `.github/workflows/integration.yml` runs the same
recipe on every relevant pull request: compile the app bundle, boot a backend
on 8301, run the suite against the runner's system Chrome. On failure the job
uploads `test-results/` (traces) and the backend log as an artifact.

## Conventions

- Role- and label-based locators (`getByRole`, `getByLabel`) with auto-waiting
  assertions. No `waitForTimeout`, no CSS-class locators.
  - One exception, and it needs the reason stated at the call site:
    asserting that something *never* appears. Auto-waiting expresses "wait
    until true", so there is nothing for it to wait for, and the only way to
    establish a negative is to let time pass first. `dictionary-focus.spec.js`
    does this through a named `nothingHappensFor` helper — a bare
    `waitForTimeout` before a positive assertion is still a flake waiting to
    happen and still banned.
- Each test gets a fresh browser context, so client-side storage starts empty
  and tests are order-independent.
- Geometry and performance specs additionally read `boundingBox()` and
  `window.__metrics()`. The latter exists only in a development build
  (`shadow-cljs compile`, never `release`) and its keys keep their
  ClojureScript spelling: `metrics['layout-shift']`, not
  `metrics.layoutShift`.

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

## Metrics

A development build exposes measurements on the page. `metrics.spec.js` reads
them; so can any CDP eval.

- `window.__metrics()` — synchronous snapshot.
- `window.__metricsReset()` — clears our copy of the counters.
- `window.__storage()` — a **promise** of `{usage, quota, usage-details}`.
  Separate because `navigator.storage.estimate()` is asynchronous.

Keys are kebab-case. `clj->js` keeps ClojureScript keyword names as written,
so it is `long-frames`, never `longFrames`; reading the camelCase spelling
yields `undefined` silently.

| Key | What it holds |
|---|---|
| `renders`, `dispatches`, `nested-dispatches`, `frames` | app-level counters; no web metric covers these |
| `web-vitals` | `{cls, fcp, inp, lcp, ttfb}`, each `{value, rating, attribution}` |
| `long-frames` | `long-animation-frame` entries: `blocking-duration`, `duration`, `start-time`, `scripts` |
| `layout-shift` | every shift, filtered by nothing: `score`, `input-excluded`, `entries` |
| `dictionary` | `ready-ms` plus the worker's own `phases` |

Two caveats worth knowing before writing an assertion:

- The web-vitals figures accumulate over the page's whole life. `__metricsReset()`
  clears our copy but does not rewind CLS or INP — the library keeps its own state,
  so after a reset it re-reports only when a *new* worst interaction or a new shift
  appears. A spec that resets and then waits for INP will wait forever; read it
  before the reset as well and take the larger of the two.
- `web-vitals.cls` cannot see a shift the user's own typing caused. CLS is defined
  to drop every `layout-shift` entry carrying `hadRecentInput`, which the browser
  sets on everything within 500 ms of a keystroke. Measured on the phone add form
  (#289): 11 entries, 0.028 total, `hadRecentInput` on all eleven, CLS 0.000 — while
  the same page given a shift with no input near it reported 0.224. Shrinking the
  viewport, which is what a software keyboard does, emits no entries at all. That is
  why `layout-shift` exists beside it and why layout specs assert on geometry.
- `ready-ms` is absent, not zero, when the dictionary never opens. A test that
  reads it must wait for it rather than compare against `0`.

`long-animation-frame` is Chrome-only. Elsewhere `long-frames` stays empty
instead of raising, so a spec asserting entries exist must run under Chrome —
which the suite does (`channel: "chrome"`).

## Out of scope

- **Service worker / offline** — not asserted here.
- **Sync / CouchDB** — the backend runs alone, no replication target exists.
  "Push to server" therefore reduces to "the word persists across a reload".
- **Sync-menu dialog** — it renders only for a provisioned account (invite
  gate, ADR-0006); a fresh environment has none, so the backdrop-dismiss
  scenario exercises the install guide instead.
