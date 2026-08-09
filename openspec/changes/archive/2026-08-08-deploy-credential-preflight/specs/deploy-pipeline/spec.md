## ADDED Requirements

### Requirement: Deploy fails before install when a unit credential is missing on the target

Before installing the infra deb, the deploy pipeline SHALL extract the credential names referenced via `LoadCredentialEncrypted=` by the units in the package and verify that each corresponding file exists in `/etc/credstore.encrypted` on the target. When any are absent, the deploy SHALL fail before the package is uploaded, reporting the missing names — names only, never credential values or contents.

#### Scenario: A referenced credential is absent

- **WHEN** a shipped unit references a credential whose file does not exist on the target
- **THEN** the deploy fails before the deb is uploaded or installed
- **AND** the failure lists the missing credential names and nothing else about them

#### Scenario: All referenced credentials are present

- **WHEN** every credential name extracted from the shipped units exists on the target
- **THEN** the preflight passes and the deploy proceeds to upload and install

#### Scenario: The checker is not yet on the target

- **WHEN** the target has never installed a deb that ships the credential checker
- **THEN** the preflight reports that the checker is absent and lets the deploy proceed, so the deb that delivers the checker can install

#### Scenario: Extraction is covered by CI

- **WHEN** infra checks run on a pull request touching infra
- **THEN** the extraction script's test suite runs and must pass
