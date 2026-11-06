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
- **AND** it starts the example fetcher component through the runtime lifecycle, after the component that writes the session cookie; the fetcher sends nothing before it is ready (`specs/example-backfill/spec.md`)
- **AND** it installs Nexus dispatch, Replicant rendering, document listeners, PWA init, and passive Service Worker registration
- **AND** it starts following the browser history last

### Requirement: Nexus dispatches all actions and effects
The system SHALL use Nexus as the mechanism for mutating app state; UI events emit action maps, effect handlers perform side effects and write results into state.

#### Scenario: UI event dispatches an action
- **WHEN** the user interacts with a UI element carrying a Nexus action
- **THEN** Nexus dispatches the action map using the runtime app context as its `system` value
- **AND** registered effect handlers can access app capabilities through effect context or the runtime app context
- **AND** the effect handler writes its result into the app-state store when state changes are needed

#### Scenario: An entered window loads its page data
- **WHEN** the open windows change: a tap, a close, the browser's Back or Forward, the app's start
- **THEN** the window on top is entered once, through runtime dispatch
- **AND** the entry computes the page slice from the learner's data in app state, synchronously, without a storage read
- **AND** Replicant renders the page view in the same task, except for the window entered at boot, which is first rendered as the `learner-data-memory` requirement "The app opens on a splash until memory is loaded" states
