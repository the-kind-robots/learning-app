## MODIFIED Requirements

### Requirement: Replicant renders all UI from state
The system SHALL use Replicant to diff and apply hiccup data to the DOM; the Service Worker SHALL NOT render application UI or route-specific HTML at runtime, but it MAY return a cached static app shell document for navigation fallback.

#### Scenario: Initial render
- **WHEN** the app state atom is first populated after boot
- **THEN** Replicant renders the current page hiccup on the document body

#### Scenario: State update triggers re-render
- **WHEN** a Nexus effect writes to the app state atom
- **THEN** Replicant re-renders the affected DOM from hiccup/state

#### Scenario: Offline navigation fallback still renders on main thread
- **WHEN** the Service Worker returns the cached app shell for an offline navigation request
- **THEN** `main.cljs` boots on the main thread
- **AND** Replicant renders the current route from app state
- **AND** the Service Worker does not render route-specific UI

### Requirement: Service Worker is a passive cache layer only
The system SHALL keep the Service Worker limited to static asset caching, navigation fallback to the cached app shell, and simple lifecycle/message handling; it SHALL NOT import application namespaces, execute route logic, call APIs, or render hiccup/application UI.

#### Scenario: SW uses network first for app navigations
- **WHEN** the Service Worker intercepts a same-origin navigation request for `/`, `/home`, `/words`, or `/lesson` while the network is available
- **THEN** it tries the network request first
- **AND** it uses the network response when that request succeeds
- **AND** it does not execute any ring handler or hiccup renderer

#### Scenario: SW serves cached app shell for offline app navigations
- **WHEN** the Service Worker intercepts a same-origin navigation request for `/`, `/home`, `/words`, or `/lesson`
- **AND** the network request fails because the browser is offline
- **THEN** it returns the cached app shell document from `/`
- **AND** the browser does not show its built-in offline page
- **AND** the main-thread app boots and handles the route

#### Scenario: SW does not fallback API requests to app shell
- **WHEN** the Service Worker intercepts a request for `/api/*`
- **THEN** it does not return the cached app shell document
- **AND** the request fails or succeeds according to normal network behavior

#### Scenario: SW precaches static assets
- **WHEN** the SW installs
- **THEN** it precaches the static assets listed in its manifest
