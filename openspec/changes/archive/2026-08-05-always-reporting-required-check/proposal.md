# Required checks always report

## Why

Branch protection requires "Client Tests", but the workflow ran on a path filter — a PR touching none of the paths never reports, and a required check that never reports blocks the merge forever. Hit live three times in one day (#181's openspec paths, #203 nearly trapping itself, #198/#200 stuck mid-marathon).

## What changes

The workflow fires on every PR; a cheap first job diffs changed paths against the merge base, and the heavy job runs only when they match — a skipped job satisfies protection, a never-started workflow does not. Push-to-master keeps its path filter: those runs are informational.

## Impact

`integration.yml` only. Unblocks infra-only and docs-only PRs, #198 and #200 first.
