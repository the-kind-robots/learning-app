# collections-data-model Specification

## Purpose
Augment the collections data model with a stored display order, so the switcher renders a deterministic, user-controlled card sequence across reloads and synced devices.

## ADDED Requirements

### Requirement: Collection documents carry an order field
The system SHALL store an integer `:order` field on every collection document. The main "Всё подряд" sentinel is pinned to order 0 and is not stored. New collection documents SHALL receive `(count existing-collections)` as their initial order on creation.

#### Scenario: New collection assigns next order
- **WHEN** a collection is created
- **AND** N other named collections already exist
- **THEN** the new document is stored with `:order = N` (named collections start at 1, since order 0 is reserved for the main sentinel)

#### Scenario: Listing respects order
- **WHEN** the collection list is requested
- **THEN** named collections are returned sorted by ascending `:order`
- **AND** the main "Всё подряд" sentinel is prepended at position 0

### Requirement: Existing collection documents are upgraded on first read
The system SHALL assign an order to legacy documents that lack the field, using the document's `created-at` timestamp as the deterministic source.

#### Scenario: Legacy documents are ordered by creation time
- **WHEN** the collection list is first read after deploying the order field
- **AND** some documents have no `:order` value
- **THEN** missing orders are filled by sorting on `created-at` ascending and persisted on the next write touching the document
