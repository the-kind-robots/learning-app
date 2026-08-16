## ADDED Requirements

### Requirement: Client-supplied CouchDB proxy-auth headers never reach an upstream
nginx SHALL guarantee that no client-supplied value of `X-Auth-CouchDB-UserName`, `X-Auth-CouchDB-Roles` or `X-Auth-CouchDB-Token` reaches an upstream, because CouchDB trusts those headers by construction. Every location that proxies upstream SHALL either clear all three headers or assign all three itself from its `auth_request` variables; a location SHALL NOT rely on inheriting the clearing from `server` scope, because nginx passes `proxy_set_header` down only into locations that declare none of their own. The clearing SHALL live in a single included snippet rather than being repeated per location, and no location SHALL both include the snippet and assign the headers, which would send each header twice.

#### Scenario: A client spoofs the identity header
- **WHEN** a request carrying `X-Auth-CouchDB-UserName` arrives at any location that proxies upstream
- **THEN** the upstream receives that request without the header

#### Scenario: The authenticated database path
- **WHEN** a request with a valid session reaches `/db/`
- **THEN** the three headers arriving upstream are the ones derived from the `auth_request` response, each present exactly once

#### Scenario: A location is added later
- **WHEN** a new proxying location is added to either nginx config
- **THEN** the staging smoke fails unless that location clears the headers or assigns them from `auth_request`
