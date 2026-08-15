# Proposal: mobile-add-form-stability

## Why

`src/client/instrumentation.cljs` has exposed `window.__metrics()` since #177
and the Playwright suite has run in CI since #260, but nothing connected them:
no spec read a counter. Two gaps made a naive assertion useless — every
layout-shift counter that follows the CLS rule drops entries with
`hadRecentInput`, which is every shift that happens while the user types, and
the suite set no viewport, so the phone media queries had never applied to
anything. GitHub issue: #289.

Connecting them found the defect they were meant to find. On a phone the
suggestion list opened in flow, adding 184 px of height to the add panel, so
the translation field and the submit button both dropped 184 px while the user
was still typing into the field above them. Measured on the merged tree: 11
layout-shift entries totalling 0.028, every one of them carrying
`hadRecentInput`, so the standard CLS read 0.000 throughout. The same page,
given a shift with no input near it, reported 0.224 — the metric is looking
away by definition, not failing.

## What Changes

- `instrumentation` gains `:layout-shift` beside the standard web metrics:
  `:score` over every entry, `:input-excluded` for the part CLS discards, and
  the entries themselves. The standard CLS stays exactly as
  `standard-page-metrics` left it.
- The phone suggestion list becomes an overlay, as it already is on desktop.
  It now moves nothing; the shift score is 0.
- `playwright.config.js` gains two projects: `desktop` (default viewport, every
  spec that does not opt out) and `mobile` (390x844, `hasTouch`,
  `*.mobile.spec.js`). `mobile` depends on `desktop` so timing is measured on a
  machine that is not running other browsers.
- The backend serves the dictionary from `LEARNING_APP__DICTIONARY_DIR` when
  set, its classpath resource otherwise. `clojure -X:dictionary-fixture` builds
  a ~28 KB dictionary from `test/fixtures/dictionary/lemmas.edn`, so a spec
  waits milliseconds for the SQLite worker instead of importing 37 MB into a
  fresh OPFS on every test.
- `test/browser/add-form-stability.mobile.spec.js` asserts the value field and
  panel title hold still while a word is typed, unfiltered shift stays under
  0.05, renders stay within two per keystroke, and no interaction reads as
  slow. It logs the measurements on every run.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `repo-browser-debugging`: the metrics requirement covers layout shift both
  with and without the CLS input filter.
- `browser-test-suite`: viewport-specific specs run in a named project; the
  suite serves a fixture dictionary.
- `backend-configuration`: the dictionary artifact source is configurable
  through the environment, production unset.

## Impact

- `src/client/instrumentation.cljs` — second counter on the same observer.
- `src/backend/core.clj` — the `Dictionary` section: env-configured source.
- `resources/public/css/components/autocomplete.css` — phone list overlays.
- `playwright.config.js`, `test/browser/add-form-stability.mobile.spec.js`,
  `test/browser/README.md` — the projects and the spec.
- `test/fixtures/dictionary/lemmas.edn`,
  `tools/dictionary/dictionary/fixture.clj`, `deps.edn` — the fixture build.
- `.github/workflows/integration.yml` — fixture step, env var, path filters.
