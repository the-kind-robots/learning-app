## ADDED Requirements

### Requirement: Merge order is machine-enforced
A PR declaring `Depends-on: #N` SHALL NOT be mergeable while any referenced issue or pull request is open; the enforcement SHALL come from branch protection, not from convention. A missing reference SHALL fail the same way, so a typo cannot silently unlock a merge.

#### Scenario: The blocker is open
- **WHEN** a PR body contains `Depends-on: #N` and node N is open
- **THEN** the required check fails and the merge button is disabled

#### Scenario: The blocker lands
- **WHEN** a push to master closes node N
- **THEN** the dependent's check is re-run automatically and turns green
