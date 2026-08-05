# repo-browser-debugging Specification

## Purpose
TBD - created by archiving change improve-tracked-workflow-and-project-cdp-browser-handling. Update Purpose after archive.
## Requirements
### Requirement: Project-specific CDP workflow is preferred for learning-app browser debugging
The repository SHALL prefer the project-specific CDP workflow for browser debugging of `sprecha.local` and `sprecha.de`.

#### Scenario: Browser debugging request for the learning app
- **WHEN** a browser-debugging task targets this learning app
- **THEN** the project-specific CDP skill is the preferred workflow
- **AND** generic browser tooling is treated as fallback rather than first choice

### Requirement: Local CDP workflow supports service-worker-aware refresh
The project-specific CDP workflow SHALL provide a local refresh path that forces service-worker update-on-reload before reloading the app.

#### Scenario: Local browser debugging after rebuilding the app
- **WHEN** a local debugging session needs to refresh the app after a new build
- **THEN** the project CDP wrapper can enable service-worker update-on-reload and reload the page
- **AND** the workflow reduces the chance of inspecting stale service-worker-controlled assets

### Requirement: CDP tooling failures prescribe the fix
When the CDP endpoint is not answering, the tooling SHALL fail fast and name the exact command that repairs the situation, rather than diagnosing transport health alone or timing out silently.

#### Scenario: Doctor with Chrome down
- **WHEN** `doctor` runs while the CDP endpoint is not answering
- **THEN** it reports the endpoint as failed and prints the `start-local` command to run

#### Scenario: Eval with Chrome down
- **WHEN** any page-resolving command runs while the endpoint is not answering
- **THEN** it fails within seconds — not after a multi-minute retry loop — and its error names `start-local`

#### Scenario: Chrome up but no app tab
- **WHEN** the endpoint answers but no tab matches the environment
- **THEN** the error names `start-local` as the way to open one

