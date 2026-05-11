## ADDED Requirements

### Requirement: App boots from main thread entry point
The system SHALL initialise the application from a main-thread ClojureScript entry point (`main.cljs`) on `DOMContentLoaded`, replacing the Service Worker as the application runtime.

#### Scenario: Boot sequence on page load
- **WHEN** the browser loads `index.html`
- **THEN** `main.cljs` runs on the main thread
- **AND** it mounts Replicant on the `#app` DOM element
- **AND** it initialises the Nexus action/effect dispatch loop
- **AND** it calls `db-migrations/ensure-migrated!`
- **AND** it calls `tasks/start!`

### Requirement: Replicant renders all UI from state
The system SHALL use Replicant to diff and apply hiccup data to the DOM; no HTML is served by the Service Worker at runtime.

#### Scenario: Initial render
- **WHEN** the app state atom is first populated after boot
- **THEN** Replicant renders the current page hiccup into `#app`

#### Scenario: State update triggers re-render
- **WHEN** a Nexus effect writes to the app state atom
- **THEN** Replicant re-renders only the changed portions of the DOM

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the sole mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map
- **AND** the registered effect handler runs
- **AND** the effect handler writes its result into the state atom

#### Scenario: Navigation action loads page data
- **WHEN** a navigation action is dispatched (e.g., `:action/navigate-home`)
- **THEN** the loader effect fires, reads PouchDB, and writes `{:page :home :data {...}}` into state
- **AND** Replicant renders the home view

### Requirement: App state uses page-slice shape
The system SHALL maintain app state as `{:page keyword :data map :modal nil-or-map :toast nil-or-map}`; each navigation action replaces `:data` entirely.

#### Scenario: State shape after navigation
- **WHEN** a loader effect completes for the lesson page
- **THEN** state contains `{:page :lesson :data {…lesson data…} :modal nil :toast nil}`

#### Scenario: Modal opened
- **WHEN** a token-info action is dispatched
- **THEN** `:modal` is set to `{:type :token-info :data {…}}`
- **AND** `:page` and `:data` are unchanged

### Requirement: Service Worker is a passive cache layer only
The system SHALL strip the Service Worker to precache + network-first fetch; it SHALL NOT import any application namespace or serve application HTML.

#### Scenario: SW does not handle app routes
- **WHEN** the SW intercepts a fetch for `/home` or any app route
- **THEN** it falls through to the network (network-first) or returns a precache hit
- **AND** it does not execute any ring handler or hiccup renderer

#### Scenario: SW precaches static assets
- **WHEN** the SW installs
- **THEN** it precaches the static assets listed in its manifest
