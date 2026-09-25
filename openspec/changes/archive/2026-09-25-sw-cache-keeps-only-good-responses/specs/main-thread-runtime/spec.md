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

### Requirement: The dictionary worker takes no configuration from its script URL
The Service Worker SHALL key cached static assets by path, so a query string neither misses the cached entry nor adds another. The dictionary worker SHALL NOT depend on its script URL for configuration: it SHALL load the SQLite engine from beside its own script, and a development build SHALL turn on its phase timings by message.

#### Scenario: The dictionary worker after a controlled reload
- **WHEN** a page under a controlling Service Worker, online or offline, starts the dictionary worker from the cache
- **THEN** the dictionary answers queries
- **AND** a development build reports the worker's phase timings, `cache-hit` included
