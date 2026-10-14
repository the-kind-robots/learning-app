## ADDED Requirements

### Requirement: Completion finds a lemma by its spelling without umlauts
The input SHALL find a lemma whose form is spelled with `ä`, `ö`, `ü` or `ß` when the input spells those as `a`, `o`, `u` or `ss`, as well as when it spells them `ae`, `oe`, `ue` or `ss`. A lemma found this way SHALL count as an exact match when the input is that spelling of one of its forms in full.

#### Scenario: Umlaut typed as its base letter
- **WHEN** the input is `fur` and the dictionary holds `für`
- **THEN** `für` is in the result
- **AND** it is the first row, with its exact-match flag set

#### Scenario: Umlaut typed as its digraph
- **WHEN** the input is `fuer`
- **THEN** `für` is the first row

#### Scenario: Both spellings are words
- **WHEN** the input is `schon` and the dictionary holds `schon` and `schön`
- **THEN** both are in the result
