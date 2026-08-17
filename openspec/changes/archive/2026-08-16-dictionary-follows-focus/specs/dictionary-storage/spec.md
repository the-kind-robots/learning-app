## MODIFIED Requirements

### Requirement: Dictionary worker opens SQLite via OPFS
The browser SHALL include a Dedicated Worker that downloads the content-addressed SQLite
file, imports it into `opfs-sahpool`, and opens it with official SQLite WASM for query
execution. At most one browsing context SHALL have it open at a time, and that context
SHALL be the one in the foreground — on screen and holding the keyboard: a context takes
the `sqlite-opfs-sahpool` Web Lock when it comes to the foreground and releases it when it
leaves.

The download and the import SHALL happen once for the origin rather than once per context
or once per turn, because the OPFS pool outlives every turn.

#### Scenario: Worker initialises on first boot
- **WHEN** a foreground context takes its turn and no OPFS pool file exists for the current hash
- **THEN** it downloads `dict.{hash12}.sqlite` from the server
- **AND** imports it into `opfs-sahpool`
- **AND** opens the database with `sqlite3.oo1.DB`
- **AND** posts a `{type: "ready"}` message to its page

#### Scenario: Worker uses cached file on subsequent boots
- **WHEN** a foreground context takes its turn and the current hash file already exists in the OPFS pool
- **THEN** it opens the existing file without re-downloading
- **AND** posts a `{type: "ready"}` message to its page

#### Scenario: A second context takes over without re-importing
- **WHEN** the context holding the database leaves the foreground and another context takes its turn
- **THEN** no download or import happens
- **AND** the second context opens the same OPFS file

#### Scenario: Worker removes stale dictionary files
- **WHEN** a context has opened the current hash file
- **THEN** it removes older `dict.*.sqlite` files from the OPFS pool
- **AND** reduces pool capacity so the pool does not grow unbounded

### Requirement: Main-thread autocomplete routes lookups through the worker
The active home autocomplete flow SHALL query SQLite through the worker proxy and SHALL NOT
call the legacy PouchDB dictionary lookup path. Completion results SHALL include each
suggestion's part of speech.

A completion request issued while this context has no database SHALL be answered when it
gets one, rather than resolving empty. The main thread SHALL NOT gate the request on
readiness.

#### Scenario: Completion query resolves via the worker
- **WHEN** the home suggest effect receives a non-empty German prefix and this context holds the database
- **THEN** it calls the dictionary port completions function
- **AND** SQL is executed through the SQLite worker proxy
- **AND** the result contains ranked completion maps with lemma text, translations, exact-match flag, and part of speech

#### Scenario: Query issued while this context is waiting its turn
- **WHEN** the home suggest effect receives a non-empty German prefix and this context does not hold the database
- **THEN** no PouchDB fallback query is attempted
- **AND** the request waits in the worker and is answered when this context takes its turn
- **AND** the home page shows the answer only if the input still matches the queried value
