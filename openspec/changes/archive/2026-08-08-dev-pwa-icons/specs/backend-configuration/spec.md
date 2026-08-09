# backend-configuration Delta

## ADDED Requirements

### Requirement: Dev builds ship distinct PWA identity assets

The HTML shell SHALL reference a dev manifest whose icons carry a red letter D badge when the backend runs from source, and the unbadged production manifest when running packaged, so dev and production installs are visually distinct on one device.

#### Scenario: Running from source

- **WHEN** the backend serves the HTML shell while running from a checkout
- **THEN** the manifest link points to the dev manifest
- **AND** the dev manifest lists the badged icon variants

#### Scenario: Running packaged

- **WHEN** the backend serves the HTML shell from the packaged jar
- **THEN** the manifest link points to the production manifest with the unbadged icons
