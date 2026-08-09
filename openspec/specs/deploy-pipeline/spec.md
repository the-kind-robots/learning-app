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

### Requirement: One jar recipe feeds every consumer

The uberjar SHALL be produced by a single reusable workflow that every consumer calls: it runs the recipe (`npm ci`, shadow-cljs release, `clojure -T:build uber`), publishes the jar as a build artifact, and records its sha256 as a workflow output. Consumers SHALL download that artifact rather than rebuild, so the recipe cannot drift between the deploy and the smoke.

#### Scenario: The deploy consumes the shared build

- **WHEN** a deploy run starts
- **THEN** the jar it uploads is the artifact from the reusable workflow, and the checksum it verifies on the server is the sha256 that workflow recorded at build time

#### Scenario: The smoke consumes the shared build

- **WHEN** the staging smoke runs on an infra pull request
- **THEN** the jar it delivers into the container is the artifact from the same reusable workflow, not a second in-job build

#### Scenario: The recipe changes

- **WHEN** the build recipe needs a change
- **THEN** exactly one workflow file changes, and both consumers pick it up on their next run

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

