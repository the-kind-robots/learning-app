## MODIFIED Requirements

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
