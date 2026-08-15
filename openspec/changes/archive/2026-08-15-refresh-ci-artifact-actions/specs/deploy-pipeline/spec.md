## ADDED Requirements

### Requirement: A corrupted artifact download fails the job that downloads it

Every job that downloads the build artifact SHALL verify its integrity as part of the download, and SHALL fail when the downloaded bytes do not match what the upload recorded. Integrity SHALL NOT be left to a later hand-written checksum step, because not every consumer has one: the deploy verifies the jar's sha256 before the atomic rename, while the staging smoke boots the jar directly.

#### Scenario: The download is torn

- **WHEN** a job downloads the app jar and the received bytes do not match the recorded digest
- **THEN** the download step fails and the job stops
- **AND** no later step runs against the corrupted file — the smoke does not boot it, the deploy does not upload it

#### Scenario: The download is intact

- **WHEN** the digest matches
- **THEN** the download succeeds and the job proceeds unchanged, including the deploy's own sha256 verification on the server
