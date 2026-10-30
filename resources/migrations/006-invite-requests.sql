-- -*- mode: sql; sql-product: sqlite; -*-
--
-- Invite requests the operator decides from an email. A grant copies the
-- request's `sha256` into `grants`, so the device's secret becomes its invite.

CREATE TABLE invite_requests
(
    -- sha256 of the secret the device keeps.
    sha256       TEXT    PRIMARY KEY,
    -- sha256 of the token the operator's links carry. The device knows its
    -- own hash, so the links need a token it does not know.
    token_sha256 TEXT    NOT NULL UNIQUE,
    words        INTEGER NOT NULL,
    days         INTEGER NOT NULL,
    created_at   INTEGER DEFAULT (UNIXEPOCH()),
    -- NULL while the request waits.
    decision     TEXT CHECK (decision IN ('granted', 'declined'))
);
