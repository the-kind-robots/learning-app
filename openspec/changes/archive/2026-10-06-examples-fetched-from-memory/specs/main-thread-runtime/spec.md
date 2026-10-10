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
- **AND** it starts the frontend router last
