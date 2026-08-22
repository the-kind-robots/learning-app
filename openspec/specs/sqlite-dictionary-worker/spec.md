# sqlite-dictionary-worker Specification

## Purpose
TBD - created by archiving change replicant-nexus-migration. Update Purpose after archive.
## Requirements
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
- **AND** it answers the queries asked of it with no completions

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

A worker SHALL answer every request from what its context has at that moment, and SHALL
NOT queue one for later. A request made while it has no database SHALL be answered with an
empty result, in the shape a real empty answer has; a request made after the context failed
to open the database SHALL be rejected with that failure, so a broken dictionary stays
distinguishable from a prefix with no completions. The main thread SHALL NOT gate a request
on readiness.

#### Scenario: Exec call over RPC
- **WHEN** the dictionary repo needs completions for a prefix
- **THEN** it sends a SQL `exec` request with a unique request id to the worker proxy
- **AND** the Promise resolves when the worker posts the matching response

#### Scenario: Query asked while waiting for a turn
- **WHEN** a completion request is made in a context that does not hold the database
- **THEN** the worker answers it at once with an empty result
- **AND** it does not keep the request, so the answer is not repeated when the turn comes
- **AND** the next keystroke is what asks again

#### Scenario: Query when the database cannot be opened
- **WHEN** a completion request is issued and this context failed to open the database
- **THEN** the request is rejected with that failure rather than left pending
- **AND** the suggest effect logs it and shows no suggestions

### Requirement: Worker completion result supports home autocomplete
The system SHALL return completion rows that the home add-word form can render and use to prefill translation.

#### Scenario: Non-empty completion result
- **WHEN** the input matches entries in the SQLite dictionary
- **THEN** the resolved value is a sequence of completion maps
- **AND** each result map contains lemma text, translations, and exact-match flag

#### Scenario: No-match completion result
- **WHEN** the input matches no entries
- **THEN** the resolved value is empty

### Requirement: Completion query cost scales with the answer, not the prefix range
The completions SQL SHALL select the ten winning lemmas before joining translations or computing the exact-match flag, so that per-row aggregation work is bounded by the result limit rather than by the number of surface forms matching the prefix.

#### Scenario: Short prefix costs the same order as a long one
- **WHEN** completions run for a one-letter prefix matching tens of thousands of surface forms
- **THEN** translations are aggregated only for the ten returned lemmas
- **AND** the query does not build grouping or concatenation structures over the full prefix range

#### Scenario: Rewritten query returns identical rows
- **WHEN** the rewritten query runs for any prefix
- **THEN** its rows, columns, ordering, and limit match the previous grouping query exactly
- **AND** `has_exact` still reflects whether the lemma has a surface form equal to the normalized input

