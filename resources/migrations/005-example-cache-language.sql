-- -*- mode: sql; sql-product: sqlite; -*-
--
-- The language an example is translated into.
--
-- The key now covers it, so every row present now is Russian, the only
-- language asked before, and waits for the startup re-key
-- (examples.cache/rekey!) to move it under a key that says so.

ALTER TABLE example_cache
    ADD COLUMN language TEXT NOT NULL DEFAULT 'ru';

UPDATE example_cache
   SET rekeyed = 0;
