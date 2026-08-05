## ADDED Requirements

### Requirement: A required check reports on every pull request
Every PR SHALL receive a verdict from each required check — pass, fail, or skipped — regardless of which paths it touches. A required check that cannot start is a broken merge pipeline, not a passing one.

#### Scenario: A PR outside the tested paths
- **WHEN** a PR touches no path the heavy job cares about
- **THEN** the check reports skipped and branch protection is satisfied

#### Scenario: A PR inside the tested paths
- **WHEN** a PR touches the application or its build inputs
- **THEN** the full suite runs and its verdict gates the merge
