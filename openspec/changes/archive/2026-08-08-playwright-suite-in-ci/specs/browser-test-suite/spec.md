# browser-test-suite Delta

## ADDED Requirements

### Requirement: Browser suite runs in CI

The Playwright suite SHALL run as a job in the integration workflow on pull
requests that touch application sources, the suite, or its configuration, and
on pushes to master. The job SHALL compile the app bundle, boot a dedicated
backend, and run the suite against the runner's system Chrome. The workflow
SHALL report on every pull request so the check can be required: on irrelevant
pull requests the job is skipped, which satisfies branch protection.

#### Scenario: Relevant pull request

- **WHEN** a pull request touches application sources, `test/browser/`, or
  `playwright.config.js`
- **THEN** the Browser Tests job compiles the app, starts a backend, and runs
  the suite headless against the runner's Chrome

#### Scenario: Irrelevant pull request

- **WHEN** a pull request touches none of the relevant paths
- **THEN** the Browser Tests job is skipped and still satisfies branch
  protection

#### Scenario: Suite fails in CI

- **WHEN** a spec fails during the CI run
- **THEN** the job fails and uploads the retained traces (`test-results/`) and
  the backend log as an artifact
