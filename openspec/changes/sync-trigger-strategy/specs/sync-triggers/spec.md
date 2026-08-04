## ADDED Requirements

### Requirement: No permanent live replication
The system SHALL NOT hold a continuous replication feed open per online device. Replication SHALL run as discrete one-shot passes that complete and close the connection, resolving vocab conflicts before completing.

#### Scenario: No held feed
- **WHEN** a device is online but idle
- **THEN** no continuous `_changes` feed is held open for it

### Requirement: Push on local write
The system SHALL run a replication pass shortly after any local write to the synced database, throttled and only when online, independent of the current screen and of which effect performed the write.

#### Scenario: Write propagates without a feed
- **WHEN** the user adds or edits a word, review, or collection while online
- **THEN** a one-shot pass runs shortly after, and the change reaches the server without a held feed

#### Scenario: Write burst coalesces
- **WHEN** a burst of writes happens (a lesson's reviews)
- **THEN** the first write triggers a pass immediately and the rest coalesce into periodic passes

#### Scenario: Offline write defers
- **WHEN** a write happens offline
- **THEN** it is persisted locally and synced on the next `online` event or pull

### Requirement: Pull on data-page entry
The system SHALL run a replication pass when a screen showing synced data — words, collections, or lesson — is entered while online. A running lesson SHALL NOT be altered by that pass.

#### Scenario: Entering words pulls
- **WHEN** the user navigates to the words page while online
- **THEN** a one-shot pass runs

#### Scenario: Entering a lesson pulls without disturbing it
- **WHEN** the user starts a lesson while online
- **THEN** a one-shot pass runs and its results land in the local database
- **AND** the lesson in progress is not reloaded or rebuilt

### Requirement: Pull on regaining connectivity
The system SHALL run a replication pass on the `online` event regardless of the current page, since the pass also flushes offline writes.

#### Scenario: Online pulls and flushes
- **WHEN** connectivity returns
- **THEN** a one-shot pass runs, pushing any offline writes and pulling remote changes

### Requirement: Post-pull reload is state-driven
After a pull completes, the system SHALL reload the current page's data via the load effect stored in state by the page's show action; pages that store none SHALL NOT reload.

#### Scenario: Fresh data shows on a data page
- **WHEN** a pull completes while the user is on the words or collections page
- **THEN** the page's load effect re-runs and the view shows the fresh data

#### Scenario: No reload outside data pages
- **WHEN** a pull completes while the user is on the home or lesson page
- **THEN** no reload runs

### Requirement: Offline-first render
Entering a data page SHALL render local data immediately and pull in the background, re-rendering on arrival; rendering SHALL NOT block on the network.

#### Scenario: Local-first then refresh
- **WHEN** a data page is entered
- **THEN** local data renders immediately
- **AND** when the background pull completes, the view re-renders with fresh data
