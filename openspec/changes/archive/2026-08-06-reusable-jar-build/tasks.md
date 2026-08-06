# Tasks

- [x] 1. `build-jar.yml`: `workflow_call`, the recipe, clojure/m2 cache restore/save, jar uploaded as `app-jar` artifact, sha256 as workflow output
- [x] 2. `deploy-app.yml`: build job calls the reusable workflow; deploy job downloads the artifact and keeps scp temp name, `sha256sum -c`, atomic `mv` intact
- [x] 3. `staging-smoke.yml`: build called behind the `changes` gate, smoke downloads the artifact; filter watches `build-jar.yml` too; skipped-but-reporting shape (#204) preserved
- [x] 4. Proof on this PR's own check run: the smoke fires (its file changed) and goes green consuming the artifact; deploy-app is dispatch-only, so its path is YAML/graph-checked, not run
