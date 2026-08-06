# staging-environment Specification

## ADDED Requirements

### Requirement: Infra pull requests run the smoke in CI

CI SHALL run `infra/staging/run.sh` on every pull request to master that touches `infra/**` or the smoke workflow itself, and the check SHALL report on every pull request: the workflow fires unconditionally and a cheap diff job decides between running the smoke and reporting skipped, so that a required check can never block a merge by failing to start. On failure the smoke output SHALL be preserved as a build artifact.

#### Scenario: An infra-touching pull request

- **WHEN** a pull request changes files under `infra/`
- **THEN** the smoke check builds the jar, runs `infra/staging/run.sh` on the CI runner, and turns red if any smoke assertion fails

#### Scenario: An unrelated pull request

- **WHEN** a pull request touches no infra path and not the workflow file
- **THEN** the smoke job reports skipped, satisfying branch protection without spending a runner
