# Browser suite (Playwright)

Headless Playwright specs that replace the ad-hoc CDP checks (#169). They run
inside WSL against the system Google Chrome (`channel: "chrome"`, no browser
download) — never against the shared dev stand or the visible Windows Chrome;
that one remains the CDP skill's territory.

## Running

The suite assumes a backend of its own is already listening on port 8301:

```sh
npx shadow-cljs compile app
LEARNING_APP__PORT=8301 LEARNING_APP__DB_PATH=$PWD/test-app.db \
  clojure -M:dev -m core &
```

Wait for `curl -s http://localhost:8301/` to answer, then:

```sh
npm run test:browser   # = npx playwright test
```

Config is `playwright.config.js` at the repo root: baseURL
`http://localhost:8301`, `trace: "retain-on-failure"` (traces land in
`test-results/` on failure).

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
| `dictionary` | `ready-ms` plus the worker's own `phases` |

Two caveats worth knowing before writing an assertion:

- The web-vitals figures accumulate over the page's whole life. `__metricsReset()`
  clears our copy but does not rewind CLS or INP — the library keeps its own state.
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
