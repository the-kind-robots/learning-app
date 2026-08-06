## ADDED Requirements

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
