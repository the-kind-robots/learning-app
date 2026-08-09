# browser-test-suite Specification

## Purpose
TBD - created by archiving change headless-playwright-suite. Update Purpose after archive.
## Requirements
### Requirement: Repeatable browser scenarios run headless inside WSL
The repository SHALL provide a Playwright suite that runs headless inside WSL against the system Google Chrome (`channel: "chrome"`, no browser download) and a locally started backend. The suite SHALL NOT depend on the Windows browser, the shared dev stand, or mirrored networking.

#### Scenario: Running the suite
- **WHEN** a backend is started locally and `npx playwright test` runs
- **THEN** the suite passes headless without any Windows-side cooperation
- **AND** a failing test retains a trace for inspection

#### Scenario: Interactive debugging still wanted
- **WHEN** a session needs the visible Windows browser
- **THEN** the CDP skill remains the channel for it — the suite does not replace interactive debugging

### Requirement: Specs assert user-visible behavior through accessible locators
Specs SHALL locate elements by role, label, or visible text and rely on auto-waiting assertions. Specs SHALL NOT use fixed sleeps or CSS-class locators.

#### Scenario: Adding a word
- **WHEN** a word and its translation are typed into the labeled home-form fields and submitted
- **THEN** the word is visible in the words list and survives a full page reload

#### Scenario: Route entry loads data
- **WHEN** the words route is entered directly and home is returned to
- **THEN** the shell and each page's list content render as visible text and roles

#### Scenario: Dialog closes from the backdrop
- **WHEN** a reachable dialog is open and its backdrop is clicked
- **THEN** the dialog closes, while a click inside the dialog leaves it open

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

