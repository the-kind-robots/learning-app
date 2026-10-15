## ADDED Requirements

### Requirement: The words filter finds a word typed without its umlauts
The filter SHALL match a query against a word's value and translations in their umlaut spelling as written (`ue`), and also with `ä`, `ö`, `ü` read as `a`, `o`, `u`. A query typed either way SHALL find the word.

#### Scenario: Umlaut typed as its base letter
- **WHEN** the list holds `für` and the reader types `fur`
- **THEN** `für` is among the matching rows

#### Scenario: Umlaut typed as its digraph
- **WHEN** the list holds `für` and the reader types `fuer`
- **THEN** `für` is among the matching rows
