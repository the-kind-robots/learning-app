## MODIFIED Requirements

### Requirement: Project-specific CDP workflow is preferred for learning-app browser debugging
The repository SHALL prefer the project-specific CDP workflow for browser debugging of `sprecha.localhost` and `sprecha.de`.

#### Scenario: Browser debugging request for the learning app
- **WHEN** a browser-debugging task targets this learning app
- **THEN** the project-specific CDP skill is the preferred workflow
- **AND** generic browser tooling is treated as fallback rather than first choice
