# Tasks

- [x] 1. Workflow fires on every PR to master; `changes` job diffs the PR range for `infra/**` and the workflow file itself
- [x] 2. Heavy job builds the jar the deploy-app.yml way and runs `infra/staging/run.sh`; skipped (still reporting) when irrelevant
- [x] 3. Timeout 20 minutes; smoke output uploaded as artifact on failure
- [x] 4. Proof on this PR's own check run: the workflow file is in its own paths, so the smoke must run and go green in Actions
