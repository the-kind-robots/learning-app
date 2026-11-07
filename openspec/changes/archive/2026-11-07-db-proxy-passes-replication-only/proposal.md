## Why

`/db/` lets any device with a valid token reach every CouchDB endpoint, with a body of any encoding and any number of connections. Replication needs eight request shapes. Everything else is attack surface, today for every invited account and tomorrow for any open door (#795).

## Changes

- nginx answers `/db/` requests that replication does not make with 403, before authentication.
- Compressed request bodies are refused with 415. Below 3.5.3, CouchDB checks its body limit against the compressed size only (CVE-2026-90797).
- The body cap is explicit, and a checkpoint (`_local`) write has a small cap of its own.
- Change feeds other than the plain one-shot feed are refused.
- Connections per client address are limited, and timeouts are shorter.
- Throttled (429) answers are not written to the access log.
- The development proxy carries the same rules.

## Capabilities

### New Capabilities

### Modified Capabilities
- `production-service-hardening`: adds the rule that the database proxy passes only replication's requests within body and connection limits.

## Decisions

- The allow-list is one `map` on method and path plus `if … return` in the existing location. Rejected: a regex location per path, because each would repeat the auth block and the `/db/` strip, and the smoke check would misread nested locations.
- The `_local` cap is a `Content-Length` pattern in the same map. Rejected: a separate location with its own `client_max_body_size`.
- The body cap lives in nginx only, now that compressed bodies are refused. Rejected: the same number again in CouchDB's `max_document_size` and `max_http_request_size`.
- The cap stays at 1 MB. The largest real document with its revision history is about 3 KB, so a batch of 100 is about 300 KB.
- Live feeds are refused, because ADR 0007 replicates in one-shot passes. #730 would reopen them.
- No per-account limits here. `limit_req` and `limit_conn` run before `auth_request`, so per-seat budgets belong to the backend (#798). That issue also bounds the number of `_local` documents.
