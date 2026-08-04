## ADDED Requirements

### Requirement: Service Worker version is derived from the built assets
The served worker SHALL carry a version derived from the contents of the static assets it caches, computed while those assets are still files and stored in the artifact. The server SHALL NOT enumerate a directory at request time to obtain it.

#### Scenario: A release changes an asset
- **WHEN** a build runs after any file under `public/` changed
- **THEN** the version served with the worker differs from the previous build's
- **AND** browsers treat the worker as changed and install it

#### Scenario: A release changes nothing
- **WHEN** a build runs with the assets unchanged
- **THEN** the version is the same as before, and browsers keep the installed worker

#### Scenario: Running from source
- **WHEN** the server runs from a source checkout, where no version was stamped
- **THEN** it computes the version from the assets on disk, so a rebuild during development is picked up
