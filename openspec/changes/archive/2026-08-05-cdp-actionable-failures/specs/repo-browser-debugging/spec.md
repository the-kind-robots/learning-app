## ADDED Requirements

### Requirement: CDP tooling failures prescribe the fix
When the CDP endpoint is not answering, the tooling SHALL fail fast and name the exact command that repairs the situation, rather than diagnosing transport health alone or timing out silently.

#### Scenario: Doctor with Chrome down
- **WHEN** `doctor` runs while the CDP endpoint is not answering
- **THEN** it reports the endpoint as failed and prints the `start-local` command to run

#### Scenario: Eval with Chrome down
- **WHEN** any page-resolving command runs while the endpoint is not answering
- **THEN** it fails within seconds — not after a multi-minute retry loop — and its error names `start-local`

#### Scenario: Chrome up but no app tab
- **WHEN** the endpoint answers but no tab matches the environment
- **THEN** the error names `start-local` as the way to open one
