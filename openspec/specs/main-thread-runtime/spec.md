# main-thread-runtime Specification

## Purpose
TBD - created by archiving change replicant-nexus-migration. Update Purpose after archive.
## Requirements
### Requirement: App boots from main thread entry point
The system SHALL initialise the application from a main-thread ClojureScript entry point (`main.cljs`) loaded by the backend app shell, replacing the Service Worker as the application runtime.

#### Scenario: Boot sequence on page load
- **WHEN** the browser loads the app shell
- **THEN** `main.cljs` runs on the main thread
- **AND** it starts the client runtime component graph
- **AND** it creates the app-state store outside the DOM
- **AND** it runs PouchDB migrations before storage-backed ports are exposed
- **AND** it starts the dictionary worker proxy and stable dictionary port handle
- **AND** it starts the task runner through the runtime lifecycle
- **AND** it installs Nexus dispatch, Replicant rendering, document listeners, PWA init, and passive Service Worker registration
- **AND** it starts the frontend router last

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

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map using the runtime app context as its `system` value
- **AND** registered effect handlers can access app capabilities through effect context or the runtime app context
- **AND** the effect handler writes its result into the app-state store when state changes are needed

#### Scenario: Navigation route loads page data
- **WHEN** the browser route changes to `/home`, `/words`, or `/lesson`
- **THEN** the `reitit.frontend` controller dispatches the page loader effect through runtime dispatch
- **AND** the loader reads needed capabilities from context instead of constructing global DB/worker handles
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

### Requirement: Service Worker version is derived from the built assets
The served worker SHALL carry a version derived from the contents of the static assets it caches, computed while those assets are still files and stored in the artifact. The server SHALL NOT enumerate a directory at request time to obtain it.

#### Scenario: A release changes an asset
- **WHEN** a build runs after any file under `public/` changed
- **THEN** the version served with the worker differs from the previous build's
- **AND** browsers treat the worker as changed and install it

#### Scenario: A release changes nothing
- **WHEN** a build runs with the assets unchanged
- **THEN** the version is the same as before, and browsers keep the installed worker

#### Scenario: Running from source
- **WHEN** the server runs from a source checkout, where no version was stamped
- **THEN** it computes the version from the assets on disk, so a rebuild during development is picked up

