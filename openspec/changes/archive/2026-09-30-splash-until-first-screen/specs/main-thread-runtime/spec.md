## MODIFIED Requirements

### Requirement: Replicant renders all UI from state
The system SHALL use Replicant to diff and apply hiccup data to the DOM; the Service Worker SHALL NOT render application UI or route-specific HTML at runtime, but it MAY return a cached static app shell document for navigation fallback. The first render SHALL happen as the `learner-data-memory` requirement "The app opens on a splash until the words and collections are read" states; from then on renders SHALL follow store changes without reordering effects: every store change renders, a write that changes nothing renders nothing, and a store write outside any dispatch renders immediately. Several changes inside one dispatch MAY render several times — extra renders inside a synchronous dispatch cost diffing, not paints (#213).

#### Scenario: Initial render
- **WHEN** the app is opened and the first render happens as the `learner-data-memory` requirement "The app opens on a splash until the words and collections are read" states
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

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map using the runtime app context as its `system` value
- **AND** registered effect handlers can access app capabilities through effect context or the runtime app context
- **AND** the effect handler writes its result into the app-state store when state changes are needed

#### Scenario: Navigation route loads page data
- **WHEN** the browser route changes to `/home`, `/words`, `/lesson` or `/collections`
- **THEN** the `reitit.frontend` controller dispatches the page's entry through runtime dispatch
- **AND** the entry computes the page slice from the learner's data in app state, synchronously, without a storage read
- **AND** Replicant renders the page view in the same task, except for the route entered at boot, which is first rendered as the `learner-data-memory` requirement "The app opens on a splash until the words and collections are read" states
