## Why

Repo-local delivery and browser-debugging workflows still have a few friction points:

- the orchestration skill was too narrow and could be skipped for warnings or small proposed changes that still should land as commits
- the GitHub Project wrapper assumes a `Category` field exists, but this project's board only exposes `Status`
- browser debugging can fall back to generic browser flows even though this app is service-worker-driven and is better inspected through the project-specific CDP wrapper
- local browser sessions can easily inspect stale service-worker state instead of the newest app build

These gaps create wasted time and make the tracked workflow less reliable than intended.

## What Changes

- Broaden the repo task orchestration trigger so likely repo-bound changes default to tracked delivery.
- Make tracked delivery reset task boundaries after closeout so the next non-trivial repo scope starts a new tracked task by default.
- Make the GitHub Project start-work wrapper tolerate projects that do not expose optional single-select fields such as `Category`.
- Make the project-specific CDP workflow the preferred browser-debugging path for this app.
- Add service-worker-aware CDP helpers so local debugging can force service-worker update-on-reload and avoid stale builds.

## Capabilities

### New Capabilities
- `repo-browser-debugging`: Defines the preferred project-specific browser debugging workflow for the learning app.

### Modified Capabilities
- `repo-delivery-workflow`: Clarifies when tracked delivery must start and how project field assignment should behave when optional fields are absent.

## Impact

- Affected code: `.codex/skills/repo-task-delivery/`, `.codex/skills/learning-app-cdp/`, `.codex/skills/gh-project-workflow/`, `codex/rules/learning-app-cdp.rules`.
- Affected behavior: tracked task kickoff, GitHub Project issue creation, and browser debugging workflow selection for this repo.
- Validation focus: skill loading, workflow start on Project V2 without `Category`, and service-worker-aware CDP refresh behavior.
