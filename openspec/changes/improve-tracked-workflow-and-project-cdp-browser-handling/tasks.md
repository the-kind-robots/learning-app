## 1. Tracked Delivery Trigger

- [x] 1.1 Expand the orchestration skill so likely repo-bound changes, warnings, and proposed improvements default to tracked delivery.
- [x] 1.2 Make the skill metadata strong enough for implicit invocation in that broader set of cases.

## 2. GitHub Project Wrapper Resilience

- [x] 2.1 Update the start-work wrapper to tolerate missing optional project fields such as `Category`.
- [x] 2.2 Keep `Status` assignment working on the current project.

## 3. Project CDP Browser Workflow

- [x] 3.1 Make the project-specific CDP skill the preferred browser-debugging path for this repo.
- [x] 3.2 Add service-worker-aware refresh/update commands to the project CDP wrapper for local debugging.
- [x] 3.3 Update rules or metadata as needed so the preferred CDP workflow is easy to invoke.

## 4. Verify

- [x] 4.1 Confirm the updated orchestration skill loads without YAML/frontmatter errors.
- [x] 4.2 Confirm the GitHub issue-start workflow works against the current project without a `Category` field.
- [x] 4.3 Run smoke checks on the CDP wrapper command parsing and the new service-worker-aware refresh flow.
