# Tasks

- [x] 1. Fetch the real commit sha of tools.build tag v0.10.14 via GitHub API; fix `deps.edn`
- [x] 2. Audit every other `:git/tag` + `:git/sha` pair in the repo the same way (only `tools/dictionary/deps.edn` test-runner v0.5.1 — consistent)
- [x] 3. Prove resolution from cold caches: `GITLIBS=<fresh> clojure -Sdeps '{:mvn/local-repo "<fresh>"}' -T:build uber`
