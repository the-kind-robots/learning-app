## MODIFIED Requirements

### Requirement: Dictionary handle is ready before dictionary data
The dictionary port MUST expose a stable handle before the dictionary data is fully ready,
and that handle MUST be callable at any point after it exists. The port SHALL NOT expose a
readiness predicate: a caller has nothing to decide with it, because a completion request
made before the data is there is held by the dictionary worker and answered when it is.

#### Scenario: Autocomplete before dictionary data is ready
- **WHEN** the router has started and the dictionary worker is still fetching, importing, or opening dictionary data
- **THEN** the dictionary capability exists in capabilities
- **AND** `:dictionary/completions` accepts the call rather than refusing it
- **AND** the answer arrives once the data is there, or the call rejects if it never will be
- **AND** page startup is not blocked by dictionary data readiness

#### Scenario: Autocomplete after dictionary data is ready
- **WHEN** the dictionary worker has opened the current SQLite dictionary
- **THEN** `:dictionary/completions` queries the dictionary worker normally
