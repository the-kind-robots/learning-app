## ADDED Requirements

### Requirement: The Service Worker caches only successful responses
The Service Worker SHALL write a network response to its cache only when the response is successful (`ok`) and same-origin (`basic`). A failed or opaque response SHALL reach the page and SHALL NOT replace or add a cache entry.

#### Scenario: An asset fails once
- **WHEN** a static asset missing from the cache is requested and the network answers 500
- **THEN** the page receives the 500
- **AND** the next request for that asset goes to the network, and its successful answer is cached

#### Scenario: The manifest fails while a copy is cached
- **WHEN** the network answers `/dictionary/manifest` with an error status
- **THEN** the page receives the error
- **AND** the cached manifest stays as it was

### Requirement: A cached asset is served under the URL it was requested by
The Service Worker SHALL key cached static assets by path, so a query string neither misses the cached entry nor adds another. The page SHALL see every response served from that cache under the request's URL, query included.

#### Scenario: The dictionary worker after a controlled reload
- **WHEN** a page under a controlling Service Worker, online or offline, starts the dictionary worker with `?sqlite3.dir=/js&telemetry=1`
- **THEN** the worker's own location carries both parameters
- **AND** a development build reports the worker's phase timings
