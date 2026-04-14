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

