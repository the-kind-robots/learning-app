## ADDED Requirements

### Requirement: The database proxy passes only replication's requests
The reverse proxy in front of CouchDB SHALL forward under `/db/` only the requests that one-shot PouchDB replication makes:
- reading the server root and a user database's info;
- reading the change feed in its plain, one-shot form;
- `_revs_diff`, `_bulk_docs` and `_bulk_get`;
- reading a document;
- reading and writing a checkpoint document under `_local/`.

Every other method or path SHALL be answered `403` before it is authenticated or reaches CouchDB. That includes a change feed asked for in any other form (`longpoll`, `continuous`, `eventsource`), database creation, design documents, `_find`, `_replicate`, `_purge`, attachments and every server-level endpoint other than the root.

A request that carries a `Content-Encoding` SHALL be answered `415`, because CouchDB may measure a compressed body against its limit before decompressing it. A request body SHALL NOT exceed 1 MB. A checkpoint write SHALL declare its length, and that length SHALL be under 10 KB.

The proxy SHALL limit concurrent connections per client address and answer the excess `429`. Throttled answers SHALL NOT be written to the access log, so a flood cannot fill the disk through the log.

The development proxy config SHALL carry the same rules, so the stand exercises them before production does.

#### Scenario: A replication pass through the proxy
- **WHEN** a device with a valid token runs one push-and-pull replication pass against its database
- **THEN** every request of the pass is forwarded and the pass completes

#### Scenario: An endpoint replication does not use
- **WHEN** a request asks for `_find`, `_replicate`, `_purge`, a design document, an attachment, `_all_dbs` or `_session` under `/db/`
- **THEN** the proxy answers `403` and nothing reaches CouchDB or the auth check

#### Scenario: A live change feed
- **WHEN** a request reads `_changes` with `feed=longpoll` or `feed=continuous`
- **THEN** the proxy answers `403`

#### Scenario: A compressed body
- **WHEN** a request to `/db/` carries `Content-Encoding: gzip`
- **THEN** the proxy answers `415`

#### Scenario: An oversized checkpoint
- **WHEN** a checkpoint write declares a length of 10 KB or more, or declares none
- **THEN** the proxy answers `403`

#### Scenario: Too many connections from one address
- **WHEN** one client address holds more connections to `/db/` than the limit allows
- **THEN** the excess requests are answered `429`
