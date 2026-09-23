## ADDED Requirements

### Requirement: A dictionary reload leaves the app reading the dictionary through its index

A successful dictionary import SHALL restart the application, with no human step and whoever started
the import — the deploy, or an operator following the runbook. The import drops and rebuilds the
database, a database rebuilt from scratch carries no index, and the application is what creates the
indexes it queries through.

The dictionary deploy SHALL then prove the result rather than assume it: it SHALL ask the database
how it plans the lookup's own selector, and SHALL fail when the answer is a full read of the
dictionary instead of that index. A deploy that leaves the lookup unindexed is a deploy that has
silently taken a feature away, and it SHALL NOT report success.

#### Scenario: An import succeeds

- **WHEN** the dictionary import finishes successfully
- **THEN** the application is restarted, and creates the index as it starts

#### Scenario: An import fails

- **WHEN** the dictionary import fails
- **THEN** the application is not restarted

#### Scenario: The deploy checks the plan

- **WHEN** the dictionary deploy has run the import
- **THEN** it asks the database to explain the lookup's selector and reads back which index answers it
- **AND** the deploy succeeds only once that answer names the lookup's index

#### Scenario: The plan is a full read

- **WHEN** the explained plan still names the all-documents scan after the deploy has waited for the
  restarted application
- **THEN** the deploy fails and says which index answered instead
