## ADDED Requirements

### Requirement: A malformed query does not stop the app
An address whose query is not valid percent-encoding SHALL open the screen of its path. The query SHALL be dropped from the address.

#### Scenario: Opening home with a malformed query
- **WHEN** the app is opened at `/home?x=%`
- **THEN** home is on display
- **AND** the address is `/home`
- **AND** the screens open from home as usual
