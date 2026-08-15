## 1. Survey

- [x] 1.1 Confirm which dependencies are actually stale, across npm, Maven, git deps and GitHub Actions — antq over `deps.edn`, `shadow-cljs.edn`, `lib/db` and `tools/dictionary` reported no stale library anywhere; every npm package matched its published latest. Only the two artifact actions came back behind
- [x] 1.2 Read the release notes for every major being skipped and record which breaking changes reach this repo — none do; the reasoning is in the proposal

## 2. Artifact actions

- [x] 2.1 `actions/upload-artifact` v4.6.2 → v7.0.1 in `build-jar.yml`, `integration.yml`, `staging-smoke.yml`
- [x] 2.2 `actions/download-artifact` v4.3.0 → v8.0.1 in `deploy-app.yml`, `staging-smoke.yml`
- [x] 2.3 Confirm no call site passes a parameter whose meaning changed, and none downloads by ID — read `action.yml` at both new tags: `name`, `path`, `if-no-files-found` and `retention-days` all still exist, both declare `using: node24`, and both downloads fetch by `name`

## 3. Clojure version alignment

- [x] 3.1 `deps.edn` `:build` alias: `org.clojure/clojure` 1.12.4 → 1.12.5

## 4. Verification

- [x] 4.1 Workflow YAML parses and the action references resolve to existing tags — all eight workflows parse; `v7.0.1` and `v8.0.1` resolve upstream
- [x] 4.2 `clojure -T:build uber` builds the jar with the aligned Clojure version — jar produced; the `:build` classpath carries `clojure-1.12.5.jar` and the jar's own `clojure/version.properties` reads 1.12.5
- [x] 4.3 The pull request exercises both artifact consumers end to end: build → upload → download → smoke — every check on PR #303 passed, container smoke included. Its download step logged `digest-mismatch: error`, an expected digest, and a matching computed digest before unzipping, so the new integrity requirement is exercised rather than assumed
- [x] 4.4 `openspec validate refresh-ci-artifact-actions --strict`
