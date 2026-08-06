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

### Requirement: Configuration shipped in the deb is in effect when postinst finishes

When the deb ships configuration that a running daemon reads only at startup, postinst SHALL restart that daemon if and only if the shipped content changed since the previous install, and SHALL do so before any postinst step that depends on the daemon being ready. Installs that ship unchanged content SHALL NOT restart the daemon.

#### Scenario: An upgrade changes the CouchDB ini

- **WHEN** the deb installs over a running CouchDB and the shipped `10-learning-app.ini` differs from the content stamped at the previous install
- **THEN** postinst restarts couchdb.service before its readiness wait and admin configuration, and the new configuration is live when postinst exits

#### Scenario: A re-install ships the same ini

- **WHEN** the deb installs and the shipped ini matches the stamp
- **THEN** CouchDB is not restarted

