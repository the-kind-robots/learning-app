# dictionary-storage Specification

## Purpose
TBD - created by archiving change dictionary-footprint-optimization. Update Purpose after archive.
## Requirements
### Requirement: Dictionary build emits SQLite artifact and enrichment-meta side file
The build tool SHALL produce a `dictionary.sqlite` containing all lemmas, translations, labels, translation-label links, and surface-form index data. Entries without Russian translations SHALL have no rows in the `translations` table; the file SHALL be deployable as-is without running enrichment. The build SHALL also emit `enrichment-meta.jsonl` as a build/enrichment support file containing senses context and priority data. `enrichment-meta.jsonl` SHALL NOT be required by the browser runtime.

#### Scenario: SQLite artifact is created
- **WHEN** the dictionary build runs
- **THEN** a file named `dictionary.sqlite` is written to the build output directory
- **AND** the file contains `lemmas`, `translations`, `labels`, `translation_labels`, and `surface_forms` tables
- **AND** the shipped `lemmas` table does not include the build-only `text_id`
- **AND** lemmas without Russian translations have no rows in the `translations` table
- **AND** a file named `enrichment-meta.jsonl` is also written to the build output directory

### Requirement: Translation enrichment is optional and applied in-place
The system SHALL treat translation enrichment as a separate, optional step that patches an existing `dictionary.sqlite` with Russian translations. The build output SHALL be deployable before enrichment runs.

The enrichment tool SHALL query `dictionary.sqlite` for untranslated lemmas, load senses context from `enrichment-meta.jsonl`, call a translation API, and write results to a durable side-file `enrichment-output.jsonl`. A separate `apply-enrichment!` step SHALL apply the side-file to `dictionary.sqlite` using gap-fill semantics: only lemmas with no existing translations are patched. Kaikki-sourced translations SHALL NOT be overwritten.

`enrichment-output.jsonl` SHALL be the authoritative translation record. If `dictionary.sqlite` is rebuilt from Kaikki, the patch SHALL be reusable without re-running the translation API.

#### Scenario: Untranslated lemmas are queried from the database
- **WHEN** the enrichment tool runs
- **THEN** it queries `dictionary.sqlite` for lemma rows absent from the `translations` table
- **AND** loads senses context and priority data for those lemmas from `enrichment-meta.jsonl`
- **AND** does not depend on a separately emitted `enrichment-input.jsonl` file

#### Scenario: Enrichment output is written to a side-file
- **WHEN** the enrichment tool receives translations from the API
- **THEN** it appends `{id, translation}` records to `enrichment-output.jsonl`
- **AND** the file persists independently of any subsequent Kaikki rebuild

#### Scenario: Apply-enrichment patches with gap-fill semantics
- **WHEN** `apply-enrichment!` runs against `dictionary.sqlite` with `enrichment-output.jsonl`
- **THEN** it INSERTs translation rows only for lemmas absent from the `translations` table
- **AND** lemmas already having Kaikki-sourced translations are not modified
- **AND** the entire apply runs in a single transaction
- **AND** patch records whose lemma key is absent from the database are skipped

#### Scenario: surface_forms table is clustered for lookup
- **WHEN** the SQLite artifact is inspected
- **THEN** `surface_forms` has primary key `(normalized_form, lemma_id)`
- **AND** a query `SELECT lemma_id FROM surface_forms WHERE normalized_form = ?` can use the primary-key B-tree

### Requirement: Duplicate Kaikki entries are merged by [word, pos, discriminant] key
The build tool SHALL merge multiple Kaikki source entries that share the same word, part-of-speech, and discriminant into a single lemma by unioning their senses, translations, and inflected forms.

The discriminant is derived from entry-level tags: sorted gender tags for nouns (`masculine`, `feminine`, `neuter`), separability tag for verbs (`separable`, `inseparable`), or nil when no such tag is present.

Entries that share the same key but are etymologically distinct homographs (e.g. *der Abort* toilet/miscarriage, *abhängen* to-depend/to-take-down) SHALL be merged into a single lemma. This is a known limitation of the source data: the Kaikki dump does not include `etymology_number`, making these homographs indistinguishable at build time.

#### Scenario: duplicate entries are merged
- **WHEN** the build processes two Kaikki entries with identical `[word, pos, discriminant]`
- **THEN** the resulting lemma contains the union of both entries' senses, translations, and forms

#### Scenario: homographs with different discriminants are kept separate
- **WHEN** two entries share word and pos but differ in discriminant (e.g. `die See` vs `der See`)
- **THEN** they are emitted as separate lemmas with distinct IDs

### Requirement: Server serves content-addressed SQLite file
The server SHALL expose a manifest endpoint and a content-addressed SQLite artifact endpoint for the current dictionary build.

#### Scenario: Manifest returns current artifact identity
- **WHEN** a client requests `GET /dictionary/manifest`
- **THEN** the response is JSON containing the full SHA-256 hash and `dict.{hash12}.sqlite` filename for the current artifact
- **AND** the response uses `Cache-Control: no-cache`

#### Scenario: SQLite file is served with immutable metadata
- **WHEN** a client requests `GET /dictionary/dict.{hash12}.sqlite` for the current hash
- **THEN** the response has SQLite content type
- **AND** the response includes `ETag` and `Content-Hash` headers derived from the file content
- **AND** the response uses immutable cache headers

#### Scenario: Unknown filename returns 404
- **WHEN** a client requests a dictionary filename that does not match the current manifest hash
- **THEN** the server responds with HTTP 404

### Requirement: Dictionary worker opens SQLite via OPFS
The browser SHALL include a Dedicated Worker that downloads the content-addressed SQLite file, imports it into `opfs-sahpool`, and opens it with official SQLite WASM for query execution.

#### Scenario: Worker initialises on first boot
- **WHEN** the Worker starts and no OPFS pool file exists for the current hash
- **THEN** the Worker downloads `dict.{hash12}.sqlite` from the server
- **AND** imports it into `opfs-sahpool`
- **AND** opens the database with `sqlite3.oo1.DB`
- **AND** posts a `{type: "ready"}` message to the main thread

#### Scenario: Worker uses cached file on subsequent boots
- **WHEN** the Worker starts and the current hash file already exists in the OPFS pool
- **THEN** the Worker opens the existing file without re-downloading
- **AND** posts a `{type: "ready"}` message to the main thread

#### Scenario: Worker removes stale dictionary files
- **WHEN** the Worker has opened the current hash file
- **THEN** it removes older `dict.*.sqlite` files from the OPFS pool
- **AND** reduces pool capacity so the pool does not grow unbounded

### Requirement: Main-thread autocomplete routes lookups through the worker
The active home autocomplete flow SHALL query SQLite through the worker proxy and SHALL NOT call the legacy PouchDB dictionary lookup path.

#### Scenario: Completion query resolves via Worker
- **WHEN** the home suggest effect receives a non-empty German prefix and the dictionary worker is ready
- **THEN** it calls the dictionary port completions function
- **AND** SQL is executed through the SQLite worker proxy
- **AND** the result contains ranked completion maps with lemma text, translations, and exact-match flag

#### Scenario: Worker not ready returns no completions
- **WHEN** the home suggest effect runs before the dictionary worker is ready
- **THEN** no PouchDB fallback query is attempted
- **AND** no completions are shown

### Requirement: PouchDB progress storage is retained while dictionary lookup is isolated
The system SHALL keep PouchDB user/device databases in place for user progress. The active dictionary autocomplete flow SHALL NOT replicate dictionary documents through the old `dictionary-sync` flow. Legacy PouchDB dictionary lookup code MAY remain temporarily isolated, but it SHALL NOT be on the active autocomplete path.

#### Scenario: dictionary-sync does not replicate dictionary documents
- **WHEN** the client sync cycle runs
- **THEN** no CouchDB replication is attempted for dictionary entries or surface forms

#### Scenario: Legacy lookup is not on active autocomplete path
- **WHEN** the home autocomplete path is inspected
- **THEN** it calls the SQLite worker-backed repo
- **AND** it does not call the legacy PouchDB suggestion path

### Requirement: Dictionary worker telemetry is emitted
The system SHALL emit telemetry for the dictionary worker lifecycle once telemetry infrastructure exists.

#### Scenario: Worker lifecycle telemetry
- **WHEN** the dictionary worker initialises, downloads/imports SQLite, and opens OPFS
- **THEN** telemetry records duration and success/failure for each phase
- **AND** the SQLite download event includes byte size when available
