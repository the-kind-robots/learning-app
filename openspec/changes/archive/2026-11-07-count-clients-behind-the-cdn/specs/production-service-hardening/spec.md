## ADDED Requirements

### Requirement: Per-address limits count the client behind the CDN
The production proxy SHALL take the client address from the CDN's `CF-Connecting-IP` header only for a request whose peer is in the CDN's published address ranges. For every other request the client address SHALL be the socket's peer, whatever headers it carries. Every per-address limit in the proxy, and the address the proxy passes to the backend, SHALL use that client address.

The trusted ranges SHALL be kept current on the server: a timer fetches the CDN's published lists and rewrites the proxy's list of trusted ranges. The proxy SHALL be reloaded only when the list changed and the new configuration passes `nginx -t`. A failed fetch, or a list with a line that is not an address range, SHALL leave the last good list in place. Before the first successful fetch, no range is trusted.

#### Scenario: Two clients through one edge
- **WHEN** two clients reach the proxy through the same CDN edge, and one of them exhausts a per-address limit
- **THEN** the other client is still within its own budget

#### Scenario: A forged header from outside the ranges
- **WHEN** a request from an address outside the published ranges carries `CF-Connecting-IP`
- **THEN** the proxy counts and forwards the request under its socket address

#### Scenario: The published lists change
- **WHEN** the timer fetches lists that differ from the trusted ones, and the new configuration passes `nginx -t`
- **THEN** the trusted list is replaced and nginx is reloaded

#### Scenario: The lists did not change
- **WHEN** the timer fetches lists identical to the trusted ones
- **THEN** nginx is not reloaded

#### Scenario: The fetch fails
- **WHEN** a list cannot be fetched, or one of its lines is not an address range
- **THEN** the last good list stays in place and nginx is not reloaded
