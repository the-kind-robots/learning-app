# The uberjar recipe lives in one reusable workflow

## Why

Noticed in #234 review (#237): the jar recipe (`npm ci` → shadow release → `clojure -T:build uber`) is duplicated between `deploy-app.yml` and `staging-smoke.yml` and will drift — the next recipe change lands in one file and silently not the other, and the smoke stops proving what the deploy ships.

## What changes

New `.github/workflows/build-jar.yml` with `on: workflow_call`: the recipe, the clojure/m2 caching deploy-app carries today, the jar uploaded as an `app-jar` artifact, and the sha256 exposed as a workflow output. `deploy-app.yml` calls it and its deploy job downloads the artifact; scp with a per-run temp name, `sha256sum -c`, atomic `mv` are unchanged. `staging-smoke.yml` calls it behind the `changes` gate (a called workflow inherits nothing implicit, so the needs/if wiring is explicit) and the smoke job downloads instead of building; the `changes` filter now also watches `build-jar.yml`, and the #204 always-reporting shape is preserved. `infra/staging/run.sh` already consumes a prebuilt `target/learning-app.jar` — no change there.

## Impact

`.github/workflows/build-jar.yml` (new), `deploy-app.yml`, `staging-smoke.yml`. Deploy behavior on the server is byte-identical; the smoke job sheds its toolchain setup and drops to a 15-minute cap. `deploy-pipeline` gains the single-recipe requirement; `staging-environment`'s CI requirement now consumes the shared artifact.
