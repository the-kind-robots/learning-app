## ADDED Requirements

### Requirement: Dictionary SQLite runs in a Dedicated Worker
The system SHALL run the SQLite dictionary (`opfs-sahpool` VFS) inside a Dedicated Worker module; the main thread SHALL NOT access SQLite synchronously.

#### Scenario: Worker initialises on boot
- **WHEN** the main thread boots
- **THEN** it spawns the dictionary Dedicated Worker
- **AND** the worker initialises `opfs-sahpool` and opens the dictionary database
- **AND** if the local pool does not contain the current hash file, the worker fetches and imports it

#### Scenario: Worker initialisation failure is non-fatal
- **WHEN** the dictionary worker fails to initialise (e.g. network unavailable, OPFS quota)
- **THEN** the error is logged
- **AND** the worker returns empty results for all subsequent `suggest` queries

### Requirement: Main thread queries the worker via postMessage RPC
The system SHALL provide a `dictionary-worker-rpc` shim on the main thread that sends query messages to the worker and returns Promises; callers use the same `suggest` interface as before.

#### Scenario: suggest call over RPC
- **WHEN** `dictionary-worker-rpc/suggest` is called with a string input
- **THEN** it sends a request message with a unique request-id to the worker
- **AND** returns a Promise that resolves when the worker posts the matching response

#### Scenario: suggest before worker ready
- **WHEN** `suggest` is called before the worker has finished initialising
- **THEN** the Promise resolves to `{:suggestions [] :prefill nil}`

### Requirement: Worker suggest result shape is identical to the PouchDB suggest shape
The system SHALL return `{:suggestions [{:lemma-id … :lemma … :pos … :rank … :translation …} …] :prefill string-or-nil}` from the worker RPC, matching the existing dictionary suggest contract.

#### Scenario: Non-empty suggest result
- **WHEN** the input matches entries in the SQLite dictionary
- **THEN** the resolved value is `{:suggestions [<result-maps>] :prefill <string-or-nil>}`
- **AND** each result map contains `:lemma-id`, `:lemma`, `:pos`, `:rank`, `:translation`

#### Scenario: No-match suggest result
- **WHEN** the input matches no entries
- **THEN** the resolved value is `{:suggestions [] :prefill nil}`
