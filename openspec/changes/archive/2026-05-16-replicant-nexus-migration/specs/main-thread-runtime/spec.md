## ADDED Requirements

### Requirement: App boots from main thread entry point
The system SHALL initialise the application from a main-thread ClojureScript entry point (`main.cljs`) loaded by the backend app shell, replacing the Service Worker as the application runtime.

#### Scenario: Boot sequence on page load
- **WHEN** the browser loads the app shell
- **THEN** `main.cljs` runs on the main thread
- **AND** it mounts Replicant rendering
- **AND** it initialises the Nexus action/effect dispatch loop
- **AND** it calls `db-migrations/ensure-migrated!`
- **AND** it calls `tasks/start!`
- **AND** it starts the dictionary worker proxy
- **AND** it registers the passive Service Worker

### Requirement: Replicant renders all UI from state
The system SHALL use Replicant to diff and apply hiccup data to the DOM; the Service Worker SHALL NOT serve application HTML at runtime.

#### Scenario: Initial render
- **WHEN** the app state atom is first populated after boot
- **THEN** Replicant renders the current page hiccup on the document body

#### Scenario: State update triggers re-render
- **WHEN** a Nexus effect writes to the app state atom
- **THEN** Replicant re-renders the affected DOM from hiccup/state

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map
- **AND** the registered effect handler runs
- **AND** the effect handler writes its result into the state atom when state changes are needed

#### Scenario: Navigation route loads page data
- **WHEN** the browser route changes to `/home`, `/words`, or `/lesson`
- **THEN** the `reitit.frontend` controller dispatches the page loader effect
- **AND** the loader reads the needed data
- **AND** the loader writes the page slice into state
- **AND** Replicant renders the page view

### Requirement: App state uses namespaced page slices
The system SHALL maintain app state as namespaced flat page slices keyed by `:app/page`, page-specific keys, and modal keys.

#### Scenario: State shape after navigation
- **WHEN** a loader effect completes for the lesson page
- **THEN** state contains `:app/page :page/lesson`
- **AND** lesson data is stored under `:lesson/*` keys

#### Scenario: Modal opened
- **WHEN** a token-info action is dispatched
- **THEN** `:modal/type` is set to `:token-info`
- **AND** `:modal/data` contains the token-info payload
- **AND** `:app/page` and the current page slice remain intact

### Requirement: Service Worker is a passive cache layer only
The system SHALL strip the Service Worker to static asset caching and simple lifecycle/message handling; it SHALL NOT import application namespaces or serve application HTML.

#### Scenario: SW does not handle app routes
- **WHEN** the SW intercepts a fetch for `/home` or any app route not listed in its precache set
- **THEN** it falls through to the network
- **AND** it does not execute any ring handler or hiccup renderer

#### Scenario: SW precaches static assets
- **WHEN** the SW installs
- **THEN** it precaches the static assets listed in its manifest
