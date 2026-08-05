# Merge order enforced by a required check

## Why

Merge order between PRs was held by drafts and comments — a request, not a wall. #196 sat one click from a production identity outage while its ordering lived in a Hold comment.

## What changes

A `Depends-on: #N` line in a PR body becomes a merge gate: a required check fails while any referenced node is open, and branch protection disables the button. A companion job re-runs the check on dependents after each push to master. The same line is the PR-level edge of the issue DAG.

## Impact

New workflow `depends-on.yml`; `integration.yml` paths widened to `.github/workflows/**` so workflow-touching PRs keep reporting the required Client Tests. Rollout: merge first, then add the context to branch protection, then retrofit #196.
