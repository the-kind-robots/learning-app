## ADDED Requirements

### Requirement: Dictionary SQLite runs in a Dedicated Worker
The system SHALL run the SQLite dictionary (`opfs-sahpool` VFS) inside a Dedicated Worker; the main thread SHALL NOT access SQLite synchronously.

#### Scenario: Worker initialises on boot
- **WHEN** the main thread boots
- **THEN** it spawns the dictionary Dedicated Worker
- **AND** the worker initialises official SQLite WASM and `opfs-sahpool`
- **AND** it fetches `/dictionary/manifest`
- **AND** if the local pool does not contain the current hash file, the worker fetches and imports it
- **AND** it opens the dictionary database

#### Scenario: Worker initialisation failure is non-fatal
- **WHEN** the dictionary worker fails to initialise (e.g. network unavailable, OPFS quota)
- **THEN** the error is logged
- **AND** autocomplete does not block the UI

### Requirement: Main thread queries the worker via postMessage RPC
The system SHALL provide a worker proxy on the main thread that sends SQL exec messages to the worker and returns Promises resolved by matching request id.

#### Scenario: Exec call over RPC
- **WHEN** the dictionary repo needs completions for a prefix
- **THEN** it sends a SQL `exec` request with a unique request id to the worker proxy
- **AND** the Promise resolves when the worker posts the matching response

#### Scenario: Completion before worker ready
- **WHEN** the home suggest effect runs before the worker has finished initialising
- **THEN** it does not issue the SQL request
- **AND** no completion suggestions are written

### Requirement: Worker completion result supports home autocomplete
The system SHALL return completion rows that the home add-word form can render and use to prefill translation.

#### Scenario: Non-empty completion result
- **WHEN** the input matches entries in the SQLite dictionary
- **THEN** the resolved value is a sequence of completion maps
- **AND** each result map contains lemma text, translations, and exact-match flag

#### Scenario: No-match completion result
- **WHEN** the input matches no entries
- **THEN** the resolved value is empty
