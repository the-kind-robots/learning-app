# deploy-pipeline Specification

## ADDED Requirements

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
