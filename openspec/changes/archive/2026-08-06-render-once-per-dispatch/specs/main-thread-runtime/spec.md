## MODIFIED Requirements

### Requirement: Replicant renders all UI from state
The system SHALL use Replicant to diff and apply hiccup data to the DOM; the Service Worker SHALL NOT render application UI or route-specific HTML at runtime, but it MAY return a cached static app shell document for navigation fallback. Renders SHALL follow store changes without reordering effects: every store change renders, a write that changes nothing renders nothing, and a store write outside any dispatch renders immediately. Several changes inside one dispatch MAY render several times — extra renders inside a synchronous dispatch cost diffing, not paints (#213).

#### Scenario: Initial render
- **WHEN** the app state atom is first populated after boot
- **THEN** Replicant renders the current page hiccup on the document body

#### Scenario: Several state writes render per change
- **WHEN** one dispatch runs several `:effect/save` effects with distinct values
- **THEN** each save writes the store where it appears in the dispatch
- **AND** Replicant renders once per change

#### Scenario: An identical save renders nothing
- **WHEN** a save writes the value the store already holds
- **THEN** the store hands back the identical map and Replicant does not render

#### Scenario: Dispatch that changes nothing renders nothing
- **WHEN** a dispatch runs only effects that do not write the store
- **THEN** Replicant does not render

#### Scenario: Nested dispatch renders per change
- **WHEN** an effect dispatches further actions from inside a running dispatch
- **AND** state is saved at either level
- **THEN** each change renders, and the browser still paints at most once per frame

#### Scenario: Store write outside a dispatch renders immediately
- **WHEN** the app-state store is written outside any dispatch
- **THEN** Replicant renders immediately

#### Scenario: Async continuation renders as its own dispatch
- **WHEN** an async effect dispatches a save after its originating dispatch has finished
- **THEN** that continuation is a new top-level dispatch
- **AND** Replicant renders once for it

#### Scenario: Offline navigation fallback still renders on main thread
- **WHEN** the Service Worker returns the cached app shell for an offline navigation request
- **THEN** `main.cljs` boots on the main thread
- **AND** Replicant renders the current route from app state
- **AND** the Service Worker does not render route-specific UI
