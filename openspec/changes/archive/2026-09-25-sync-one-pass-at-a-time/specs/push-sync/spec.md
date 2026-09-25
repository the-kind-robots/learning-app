## ADDED Requirements

### Requirement: Sync passes run one at a time
The client SHALL run at most one sync pass at a time. A request for a pass while none runs SHALL start one. Requests that arrive while a pass runs SHALL cause exactly one more pass after it, however many arrive. Every requester SHALL be answered when the passes finish, not before. A failed pass SHALL NOT stop the next request from running one. Route entry, a poke, the `online` event and a local write SHALL all request a pass this way.

#### Scenario: Triggers at once
- **WHEN** route entry, a poke, the `online` event and a local write all ask for a pass while none runs
- **THEN** one pass runs

#### Scenario: Request during a pass
- **WHEN** one or more requests arrive while a pass runs
- **THEN** exactly one more pass runs after it

#### Scenario: Route entry soon after a pass
- **WHEN** the user opens another screen after a pass has completed
- **THEN** a pass runs

### Requirement: Local writes are batched and pulled documents request nothing
Local writes SHALL request a pass at most once every 3 s, so a burst of writes goes out in few passes. A document revision the pass itself pulled SHALL NOT count as a local write and SHALL NOT request a pass.

#### Scenario: A pull writes documents
- **WHEN** a pass pulls documents from the server and nothing else is written locally
- **THEN** no further pass follows it

#### Scenario: A lesson's reviews
- **WHEN** a learner answers several trials within 3 s and no pass is running
- **THEN** their writes go out in at most two passes

## REMOVED Requirements

### Requirement: Route entry does not pull again within 30 s of the last completed pass
**Reason**: One pass at a time with a single follow-up pass replaces the gate. Live replication, which is planned, will replace both.
**Migration**: None. Route entry requests a pass on every visit.
