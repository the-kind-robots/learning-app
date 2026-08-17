## MODIFIED Requirements

### Requirement: Dictionary SQLite runs in a Dedicated Worker
The system SHALL run the SQLite dictionary (`opfs-sahpool` VFS) inside a Dedicated Worker;
the main thread SHALL NOT access SQLite synchronously. Every browsing context SHALL have a
worker of its own, and at most one context at a time SHALL have the database open.

Which context that is SHALL follow the foreground — on screen and holding the keyboard,
`visibilityState === "visible" && hasFocus()`. Visibility alone SHALL NOT decide it: two
contexts can be visible at once, so the database would go to whichever took the lock first
rather than to the one being typed into.

A context SHALL take the `sqlite-opfs-sahpool` Web Lock — requested without `ifAvailable`,
so a context that does not win waits its turn rather than failing — when it comes to the
foreground, and SHALL close the database, pause the pool and release the lock when it
leaves. A context that is not in the foreground SHALL NOT hold the database.

The page SHALL tell its worker whether it is in the foreground, as a single boolean, both
at startup and whenever either half of the condition changes — `visibilitychange`, `focus`
and `blur` — because a worker cannot observe the document.

Between turns a context SHALL keep its pool installed and paused rather than uninstalling
it, and SHALL load the SQLite engine on its first turn rather than at startup.

#### Scenario: Foreground context opens the database
- **WHEN** a context comes to the foreground and no other context holds the pool lock
- **THEN** it takes the lock
- **AND** it loads the engine if this is its first turn
- **AND** it installs `opfs-sahpool`, or unpauses the pool it already has
- **AND** it opens the dictionary database and answers its own queries

#### Scenario: Context leaves the foreground
- **WHEN** the context holding the database is hidden, or loses the keyboard while still visible
- **THEN** it closes the database, pauses the pool and releases the lock
- **AND** it reports itself as no longer ready

#### Scenario: Waiting context has no dictionary
- **WHEN** a context is visible but is not the one in the foreground, or another context holds the lock
- **THEN** it waits its turn
- **AND** it does not open, download or import anything
- **AND** it shows no completions until its turn comes

#### Scenario: Context killed without warning
- **WHEN** the context holding the database is closed rather than backgrounded
- **THEN** the browser releases its lock
- **AND** the next context to want it opens the same OPFS file

#### Scenario: No context is in the foreground
- **WHEN** no context is in the foreground, whether hidden or merely without the keyboard
- **THEN** no context holds the database, which is a resting state and not a failure
- **AND** the first context to reach the foreground takes it

#### Scenario: Worker initialisation failure is non-fatal
- **WHEN** a context fails to open the database (e.g. network unavailable, OPFS quota)
- **THEN** it pauses the pool if it installed one, so no other context is blocked by it
- **AND** the error is logged and the lock is released
- **AND** autocomplete does not block the UI

### Requirement: Main thread queries the worker via postMessage RPC
The system SHALL provide a worker proxy on the main thread that sends SQL exec messages to
its own worker and returns Promises resolved by matching request id.

A worker SHALL hold a request made while it has no database and SHALL settle it when its
turn comes. A request SHALL NOT be discarded for arriving early, and the main thread SHALL
NOT gate a request on readiness.

#### Scenario: Exec call over RPC
- **WHEN** the dictionary repo needs completions for a prefix
- **THEN** it sends a SQL `exec` request with a unique request id to the worker proxy
- **AND** the Promise resolves when the worker posts the matching response

#### Scenario: Query asked while waiting for a turn
- **WHEN** a completion request is made in a context that does not hold the database
- **THEN** the worker holds it
- **AND** answers it when that context takes its turn, with no further prompting from the page
- **AND** the home page discards the answer if the input has changed since

#### Scenario: Query when the database cannot be opened
- **WHEN** a completion request is issued and this context failed to open the database
- **THEN** the request is rejected with that failure rather than left pending
- **AND** the suggest effect logs it and shows no suggestions
