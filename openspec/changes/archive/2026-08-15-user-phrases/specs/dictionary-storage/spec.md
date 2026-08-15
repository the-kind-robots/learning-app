# dictionary-storage Delta

## MODIFIED Requirements

### Requirement: Main-thread autocomplete routes lookups through the worker
The active home autocomplete flow SHALL query SQLite through the worker proxy and SHALL NOT call the legacy PouchDB dictionary lookup path. Completion results SHALL include each suggestion's part of speech.

#### Scenario: Completion query resolves via Worker
- **WHEN** the home suggest effect receives a non-empty German prefix and the dictionary worker is ready
- **THEN** it calls the dictionary port completions function
- **AND** SQL is executed through the SQLite worker proxy
- **AND** the result contains ranked completion maps with lemma text, translations, exact-match flag, and part of speech

#### Scenario: Worker not ready returns no completions
- **WHEN** the home suggest effect runs before the dictionary worker is ready
- **THEN** no PouchDB fallback query is attempted
- **AND** no completions are shown
