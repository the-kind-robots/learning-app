-- -*- mode: sql; sql-product: sqlite; -*-
--
-- Generated example sentences, kept under the question that produced them.
--
-- Examples live in the client's device-db, which never replicates, so every
-- device asks the provider for itself. The answer depends on nothing but the
-- question, so one row serves every device and every account that asks the same
-- one. There is no account column on purpose: a generated sentence is not
-- account data, and sharing it is the whole saving.
--
-- `question_sha256` is the digest of the normalized question, and the only key
-- anything looks a row up by. The parts it was built from are stored beside it
-- so the accumulated cache can be read by hand — by eye and by `LIKE`, not by
-- any query the app makes, which is why they carry no index.
--
-- `example` holds the example as JSON: the cache stores and returns data, and
-- the endpoint renders the response. A hit is therefore a parse and a
-- re-render, which costs nothing next to the provider call it replaces, and
-- keeps the column readable by anything that speaks JSON.

CREATE TABLE example_cache
(
    question_sha256 TEXT PRIMARY KEY,
    word            TEXT    NOT NULL,
    translations    TEXT    NOT NULL,
    context         TEXT,
    example         TEXT    NOT NULL,
    created_at      INTEGER DEFAULT (UNIXEPOCH())
);
