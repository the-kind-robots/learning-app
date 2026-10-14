-- -*- mode: sql; sql-product: sqlite; -*-
--
-- Which rows are keyed by the current rule.
--
-- The key stopped covering the prompt and the models, so rows stored before
-- are re-keyed once at startup (examples.cache/rekey!). That step needs the
-- dictionary, which a migration cannot reach. This column is what lets it
-- resume and skip the rows it already did: every row present now is waiting
-- (0), every row stored from here on is keyed by the current rule (1).

ALTER TABLE example_cache
    ADD COLUMN rekeyed INTEGER NOT NULL DEFAULT 0;
