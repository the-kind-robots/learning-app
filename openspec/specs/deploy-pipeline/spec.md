# deploy-pipeline Specification

## Purpose
TBD - created by archiving change atomic-artifact-swap. Update Purpose after archive.
## Requirements
### Requirement: Live app jar is replaced only by verified atomic rename
The deploy pipeline SHALL write the new jar to a temporary path on the same filesystem as `/opt/learning-app/learning-app.jar`, verify the upload's integrity against a checksum recorded at build time, and install it with a single rename. The live jar SHALL never be written in place, and no torn artifact SHALL ever become the live jar.

#### Scenario: Successful deploy
- **WHEN** the uploaded temp jar's sha256 matches the build's recorded checksum
- **THEN** the jar is installed via `mv -f` (rename(2), same filesystem) over the live path
- **AND** the restart watch on the live jar observes only the completed artifact

#### Scenario: Upload is torn or missing
- **WHEN** the temp jar is absent or its sha256 does not match the recorded checksum
- **THEN** the deploy fails before the rename
- **AND** the live jar and running service are untouched

#### Scenario: Two deploys run concurrently
- **WHEN** two workflow runs upload at the same time
- **THEN** each run uses its own run-id-suffixed temp path
- **AND** neither run can rename the other's partial upload into place

### Requirement: Aborted deploys do not accumulate artifacts
The deploy pipeline SHALL remove leftover temp jars in `/opt/learning-app` after a successful swap, without touching uploads recent enough to belong to an in-flight run.

#### Scenario: A previous run died between upload and swap
- **WHEN** a later deploy succeeds and a stale temp jar older than one hour exists
- **THEN** the stale temp jar is deleted
- **AND** temp jars younger than one hour are left alone

