-- -*- mode: sql; sql-product: sqlite; -*-

CREATE TABLE IF NOT EXISTS users
(
    id         INTEGER PRIMARY KEY,
    name       TEXT UNIQUE NOT NULL,
    password   TEXT,
    created_at INTEGER DEFAULT (UNIXEPOCH())
);
--;;
CREATE TABLE IF NOT EXISTS user_settings
(
    user_id  INTEGER PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    settings BLOB DEFAULT '{}'
);
--;;
CREATE TABLE IF NOT EXISTS sessions
(
    id         INTEGER PRIMARY KEY,
    user_id    INTEGER GENERATED ALWAYS AS (value ->> '$."user-id"') STORED REFERENCES users (id) ON DELETE CASCADE,
    token      TEXT UNIQUE NOT NULL,
    value      BLOB,
    created_at INTEGER DEFAULT (UNIXEPOCH())
);
--;;
CREATE TABLE IF NOT EXISTS words
(
    id          INTEGER PRIMARY KEY,
    user_id     INTEGER REFERENCES users (id),
    value       TEXT NOT NULL,
    translation TEXT NOT NULL,
    created_at  INTEGER DEFAULT (UNIXEPOCH()),
    modified_at INTEGER
);
--;;
CREATE UNIQUE INDEX IF NOT EXISTS unique_user_word ON words (user_id, value);
--;;
CREATE TABLE IF NOT EXISTS examples
(
    id          INTEGER PRIMARY KEY,
    word_id     INTEGER REFERENCES words (id) ON DELETE CASCADE ON UPDATE CASCADE,
    value       TEXT NOT NULL,
    translation TEXT NOT NULL,
    structure   BLOB,
    is_favorite INTEGER DEFAULT (0),
    created_at  INTEGER DEFAULT (UNIXEPOCH()),
    modified_at INTEGER
);
--;;
CREATE TABLE IF NOT EXISTS lessons
(
    id                   INTEGER PRIMARY KEY,
    user_id              INTEGER REFERENCES users (id) UNIQUE,
    current_challenge_id INTEGER
);
--;;
CREATE TABLE IF NOT EXISTS challenges
(
    id           INTEGER PRIMARY KEY,
    source_table TEXT,
    source_id    INTEGER,
    lesson_id    INTEGER REFERENCES lessons (id) ON DELETE CASCADE ON UPDATE CASCADE,
    passed       INTEGER DEFAULT (0)
);
--;;
CREATE TABLE IF NOT EXISTS reviews
(
    id          INTEGER PRIMARY KEY,
    word_id     INTEGER REFERENCES words (id) ON DELETE CASCADE,
    retained    INTEGER,
    reviewed_at INTEGER DEFAULT (UNIXEPOCH())
);
--;;
CREATE TABLE IF NOT EXISTS recovery_tokens
(
    sha256     TEXT    PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at INTEGER DEFAULT (UNIXEPOCH()),
    expires_at INTEGER NOT NULL
);
