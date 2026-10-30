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
    -- What the learner wrote to the operator, up to 500 characters.
    note         TEXT    NOT NULL,
    created_at   INTEGER DEFAULT (UNIXEPOCH()),
    -- When the Worker took the operator's mail; NULL until it did.
    mailed_at    INTEGER,
    -- NULL while the request waits.
    decision     TEXT CHECK (decision IN ('granted', 'declined'))
);
--;;
-- The daily cap counts the requests of the last day on every new one.
CREATE INDEX invite_requests_created_at ON invite_requests (created_at);
