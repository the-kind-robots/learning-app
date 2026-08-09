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

## Out of scope

- **Service worker / offline** — not asserted here.
- **Sync / CouchDB** — the backend runs alone, no replication target exists.
  "Push to server" therefore reduces to "the word persists across a reload".
- **Sync-menu dialog** — it renders only for a provisioned account (invite
  gate, ADR-0006); a fresh environment has none, so the backdrop-dismiss
  scenario exercises the install guide instead.
