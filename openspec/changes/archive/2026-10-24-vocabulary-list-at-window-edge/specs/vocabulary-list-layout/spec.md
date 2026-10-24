## ADDED Requirements

### Requirement: The word list scrolls at the window's edge

On the words screen with at least one word, the box that scrolls the rows SHALL span the window
from edge to edge, between the bar at the top and the search field, so its scrollbar stands at the
window's edge. The bar at the top, the search field and «Начать урок» SHALL stay in place while the
rows scroll. The rows SHALL stand centred under the search field, whether a scrollbar shows or not.

#### Scenario: Scrolling the list

- **WHEN** the reader scrolls a list taller than its box
- **THEN** the box reaches the window's edge
- **AND** the top bar, the search field and «Начать урок» stay where they were
- **AND** the rows stand centred under the search field

#### Scenario: A search that makes the list fit

- **WHEN** the reader types a query whose rows fit in the box
- **THEN** the rows stay centred under the search field and do not shift sideways
