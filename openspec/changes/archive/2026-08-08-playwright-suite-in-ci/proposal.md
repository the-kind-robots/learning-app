# Proposal: playwright-suite-in-ci

## Why

The Playwright browser suite (test/browser, #169) runs only when someone
remembers to run it locally; no workflow launches it, so a change to exactly
what a spec covers can merge with CI staying green. GitHub issue: #260.

## What Changes

- `.github/workflows/integration.yml` gains a `Browser Tests` job next to
  `Client Tests`: compile the app bundle, boot a backend on port 8301, run the
  suite against the runner's system Chrome.
- The job follows the always-reporting pattern (#204): the `changes` job gains
  a `browser` output, and a skipped job satisfies branch protection on
  irrelevant pull requests.
- On failure the job uploads Playwright traces (`test-results/`) and the
  backend log as an artifact.
- `test/browser/README.md` drops CI from "out of scope" and documents the job.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `browser-test-suite`: the suite SHALL run in CI on relevant pull requests
  and pushes to master, with traces retained as artifacts on failure.

## Impact

- `.github/workflows/integration.yml` — new job plus `changes` output.
- `test/browser/README.md` — CI section.
- No application code changes.
