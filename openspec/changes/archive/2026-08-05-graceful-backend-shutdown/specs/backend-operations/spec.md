# backend-operations

## ADDED Requirements

### Requirement: SIGTERM stops the backend gracefully
On SIGTERM the backend SHALL stop accepting connections first, then let in-flight requests finish within a bounded drain timeout before the JVM exits. Per-request SQLite connections close as their requests complete — draining is the cleanup.

#### Scenario: systemd stops the service

- **WHEN** the process receives SIGTERM while requests are in flight
- **THEN** the listening socket closes, the in-flight requests complete with their responses, and only then does the process exit

#### Scenario: shutdown with nothing in flight

- **WHEN** the process receives SIGTERM while idle
- **THEN** it stops promptly and logs that the server stopped

### Requirement: The shutdown hook is registered exactly once
The JVM shutdown hook SHALL be registered once per process, surviving the dev namespace-reload path without duplication, and SHALL NOT interfere with `restart-server!` during development.

#### Scenario: dev reload does not duplicate the hook

- **WHEN** the backend namespace is reloaded or the server is restarted in development
- **THEN** exactly one shutdown hook remains registered
