# staging-environment Specification

## MODIFIED Requirements

### Requirement: Infra pull requests run the smoke in CI

CI SHALL run `infra/staging/run.sh` on every pull request to master that touches `infra/**`, the smoke workflow itself, or the shared jar-build workflow, and the check SHALL report on every pull request: the workflow fires unconditionally and a cheap diff job decides between running the smoke and reporting skipped, so that a required check can never block a merge by failing to start. The jar the smoke delivers SHALL come from the shared reusable build workflow, downloaded as an artifact, not from an in-job rebuild. On failure the smoke output SHALL be preserved as a build artifact.

#### Scenario: An infra-touching pull request

- **WHEN** a pull request changes files under `infra/`
- **THEN** the smoke check downloads the jar built by the shared workflow, runs `infra/staging/run.sh` on the CI runner, and turns red if any smoke assertion fails

#### Scenario: An unrelated pull request

- **WHEN** a pull request touches no infra path, not the smoke workflow, and not the jar-build workflow
- **THEN** the smoke and build jobs report skipped, satisfying branch protection without spending a runner
