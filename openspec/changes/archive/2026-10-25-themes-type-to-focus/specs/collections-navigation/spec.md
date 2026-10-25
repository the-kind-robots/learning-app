## ADDED Requirements

### Requirement: Typing on the themes screen focuses the matching target
On the themes screen, characters typed without Ctrl, Alt or Meta SHALL move keyboard focus to the first target, in reading order, whose shown name contains the typed text, case ignored. Characters typed less than a second apart SHALL extend the same text; after a longer pause the next character SHALL start a new one. A space SHALL extend a text already being typed and otherwise SHALL keep its usual effect on the focused target. When no target matches, focus SHALL stay where it is.

#### Scenario: Typing part of a name
- **WHEN** the collections `Kurs / Kapitel 1`, `Kurs / Kapitel 2` and `Solo` are on the themes screen
- **AND** the user types `pit`
- **THEN** focus is on the row `Kapitel 1`

#### Scenario: Typing on narrows the match
- **WHEN** the user types `pit`, then `el 2` without a pause
- **THEN** focus is on the row `Kapitel 2`

#### Scenario: A pause starts a new search
- **WHEN** the user types `kap`, pauses for more than a second, and types `so`
- **THEN** focus is on the tile `Solo`
