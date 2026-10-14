## REMOVED Requirements

### Requirement: A dictionary reload leaves the app reading the dictionary through its index

**Reason**: No database holds the dictionary any more, so there is nothing to import, restart for or explain a plan of. The backend reads the dictionary file that ships with the app.

**Migration**: None. The dictionary reaches the server with the app artifact; rebuilding it is `clojure -X:dictionary build` followed by an ordinary deploy.

## ADDED Requirements

### Requirement: The dictionary ships with the app

A deploy SHALL need no dictionary import: no separate dictionary job, no import service and no
dictionary database. The dictionary the application reads SHALL be the one that arrives with the app
artifact.

#### Scenario: An ordinary deploy

- **WHEN** the app is deployed
- **THEN** the application reads the dictionary delivered with that artifact
- **AND** no step loads a dictionary into a database

#### Scenario: A server is provisioned

- **WHEN** the package is installed on a server
- **THEN** it creates no dictionary database and exposes no route to one
