## MODIFIED Requirements

### Requirement: Dictionary handle is ready before dictionary data
The dictionary port MUST expose a stable handle before the dictionary data is fully ready,
and that handle MUST be callable at any point after it exists. A completion request made
before the data is there resolves with no completions, so no caller SHALL gate the request
on readiness. The port SHALL expose `:dictionary/ready?` as a reading rather than a gate,
because an empty answer alone cannot say whether the prefix has no completions or this
context has no dictionary.

#### Scenario: Autocomplete before dictionary data is ready
- **WHEN** the router has started and the dictionary worker is still fetching, importing, or opening dictionary data
- **THEN** the dictionary capability exists in capabilities
- **AND** `:dictionary/completions` accepts the call rather than refusing it
- **AND** it resolves with no completions, or rejects if this context failed to open the dictionary
- **AND** `:dictionary/ready?` reports false meanwhile
- **AND** page startup is not blocked by dictionary data readiness

#### Scenario: Autocomplete after dictionary data is ready
- **WHEN** the dictionary worker has opened the current SQLite dictionary
- **THEN** `:dictionary/completions` queries the dictionary worker normally
- **AND** `:dictionary/ready?` reports true
