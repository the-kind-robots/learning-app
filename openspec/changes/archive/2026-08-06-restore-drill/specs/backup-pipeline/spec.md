# backup-pipeline Specification

## ADDED Requirements

### Requirement: A restore is proven by drill, not assumed
The staging run SHALL restore a real backup archive into the running container following the runbook's restore procedure — services stopped, files back, ownership corrected, services restarted — after destroying both stores, and SHALL verify coherence of the result: the seeded token authenticates via `/auth/check`, the seeded userdb document is served through the authenticated proxy, and the boot orphan-userdb report is clean. Each assertion SHALL report PASS or FAIL on its own line and the run SHALL exit nonzero on any FAIL. The runbook's restore section and the drill SHALL agree; a divergence is a defect in one of them.

#### Scenario: A staging run
- **WHEN** `infra/staging/run.sh` completes its smoke phase
- **THEN** the restore drill seeds data, backs it up, destroys the SQLite database and the userdb, restores from the archive, and every coherence assertion reports PASS

#### Scenario: The runbook drifts
- **WHEN** the restore procedure in `docs/ops/runbook.md` no longer matches what a restore actually requires
- **THEN** the drill fails instead of the next real restore

### Requirement: Borg key custody is documented
Operator documentation SHALL state where the borg passphrase lives (the systemd encrypted credential `/etc/credstore.encrypted/borg-passphrase`, decrypted by the backup unit), that losing the passphrase loses every archive with no recovery, how it is rotated, and a read-only command that verifies the key still opens the repository.

#### Scenario: An operator checks the key
- **WHEN** the operator runs the documented `borg list` dry check
- **THEN** success proves the stored passphrase still opens the repository without touching any archive
