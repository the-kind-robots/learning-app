## ADDED Requirements

### Requirement: A new collection's tile grows in its place

On a phone, creating a collection SHALL grow its tile from smaller in its place while the tiles after it glide apart to make room, over 240 ms — the reverse of a deletion.

#### Scenario: Creating a collection

- **WHEN** the user creates a collection with «ДОБАВИТЬ НАБОР» on the collections screen on a phone
- **THEN** its tile grows in its place and the tiles after it glide aside
