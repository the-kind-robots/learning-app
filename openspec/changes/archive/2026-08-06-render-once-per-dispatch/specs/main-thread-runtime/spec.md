## MODIFIED Requirements

### Requirement: Replicant renders all UI from state
The system SHALL use Replicant to diff and apply hiccup data to the DOM; the Service Worker SHALL NOT render application UI or route-specific HTML at runtime, but it MAY return a cached static app shell document for navigation fallback. Renders SHALL be scheduled per dispatch without reordering effects: one render for a dispatch that changed the app-state store, none for a dispatch that changed nothing, and an immediate render for a store write outside any dispatch.

#### Scenario: Initial render
- **WHEN** the app state atom is first populated after boot
- **THEN** Replicant renders the current page hiccup on the document body

#### Scenario: Dispatch with several state writes renders once
- **WHEN** one dispatch runs several `:effect/save` effects
- **THEN** each save writes the store where it appears in the dispatch
- **AND** Replicant renders exactly once, after the dispatch completes

#### Scenario: Dispatch that changes nothing renders nothing
- **WHEN** a dispatch runs only effects that do not write the store
- **THEN** Replicant does not render

#### Scenario: Nested dispatch renders once
- **WHEN** an effect dispatches further actions from inside a running dispatch
- **AND** state is saved at either level
- **THEN** Replicant renders exactly once, after the outermost dispatch unwinds

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
