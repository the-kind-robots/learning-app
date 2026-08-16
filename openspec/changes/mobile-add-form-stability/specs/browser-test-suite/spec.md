# browser-test-suite Delta

## ADDED Requirements

### Requirement: Viewport-specific specs run in a named project

The suite SHALL define a `desktop` project at the default viewport and a
`mobile` project at a phone viewport with touch, and a spec SHALL opt into the
phone layout by its filename. The `mobile` project SHALL run after `desktop`
so that specs measuring main-thread time are not competing with other browsers
for the machine.

#### Scenario: A spec named for the phone

- **WHEN** a spec file is named `*.mobile.spec.js`
- **THEN** it runs only in the `mobile` project, where the phone media queries
  apply, and is excluded from `desktop`

#### Scenario: Every other spec

- **WHEN** a spec file is named anything else
- **THEN** it runs only in `desktop`, at the viewport the suite has always
  used, unaffected by the phone project existing

### Requirement: The suite serves a fixture dictionary

The browser suite SHALL run against a small generated dictionary rather than
the shipped one, built from a source checked into the repository and served to
the backend through the environment. A spec that needs suggestions SHALL be
able to tell the fixture from the shipped dictionary by what it answers.

#### Scenario: Suggestions in a test

- **WHEN** a spec types a prefix into the add form
- **THEN** the suggestion list opens within the test's normal timeout, without
  the shipped dictionary being downloaded and imported into storage

#### Scenario: The fixture was not built or not served

- **WHEN** the backend serves the shipped dictionary to the suite instead
- **THEN** the spec fails on the number of matches it expects, rather than
  passing slowly
