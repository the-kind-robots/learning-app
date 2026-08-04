-- -*- mode: sql; sql-product: sqlite; -*-
--
-- One account = one bearer token (ADR-0006). The token replaces the
-- name + argon2 password pair, so sessions and one-time recovery tokens are
-- no longer needed: `/auth/check` resolves the account from the token itself.
--
-- Existing accounts keep their id, their settings and their CouchDB database,
-- but come out with no token — their old credential cannot be turned into one.
-- The operator issues each of them a new key.

CREATE TABLE users_new
(
    id           INTEGER PRIMARY KEY,
    token_sha256 TEXT UNIQUE,
    created_at   INTEGER DEFAULT (UNIXEPOCH())
);
--;;
INSERT INTO users_new (id, created_at) SELECT id, created_at FROM users;
--;;
DROP TABLE users;
--;;
ALTER TABLE users_new RENAME TO users;
--;;
DROP TABLE sessions;
--;;
DROP TABLE recovery_tokens;
--;;
-- Named for the gate it fills, not for its phase-1 source: a later payment
-- mints rows here too. A grant pre-dates the account it creates, so it
-- references no user.
CREATE TABLE grants
(
    sha256     TEXT    PRIMARY KEY,
    created_at INTEGER DEFAULT (UNIXEPOCH()),
    expires_at INTEGER NOT NULL,
    used_at    INTEGER
);
