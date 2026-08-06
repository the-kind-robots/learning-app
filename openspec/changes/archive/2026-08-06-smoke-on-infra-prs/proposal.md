# The container smoke runs in CI on infra PRs

## Why

The staging rung (#214) exists and already paid for itself — #225 was caught before production — but it only runs when someone remembers to run it. #230: infra PRs should get the smoke automatically, as an always-reporting check, because a required check that never starts blocks the merge forever (#204).

## What changes

New workflow `.github/workflows/staging-smoke.yml`: fires on every PR to master; a cheap `changes` job diffs the PR against its base and the heavy job — build the jar, run `infra/staging/run.sh` on ubuntu-latest — runs only when `infra/**` or the workflow itself changed, reporting skipped otherwise (the #204 shape, same as integration.yml). The job is capped at 20 minutes and uploads the smoke output as an artifact on failure. The systemd-in-docker incantation recorded in `infra/staging/README.md` is permitted on GitHub-hosted runners.

## Impact

New `.github/workflows/staging-smoke.yml`; no production or staging files change. `staging-environment` capability gains a CI requirement. Adding the check to branch protection's required list is the owner's console action, not this change.
