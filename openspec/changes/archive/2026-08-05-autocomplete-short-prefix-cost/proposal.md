# Autocomplete short prefixes stop costing 100x more than long ones

## Why

Native measurement on the shipped `dictionary.sqlite` (GH-179): prefix `fenster` 1 ms, prefix `f` 124 ms. The completions query grouped and GROUP_CONCATed translations for every lemma in the prefix range — tens of thousands of rows for one-letter prefixes — then `ORDER BY rank` + `LIMIT 10` threw nearly all of it away. Three TEMP B-TREEs in the plan, all sized by the range, not by the answer.

## What changes

- `completions-sql` in `adapters.dictionary` selects the winning ten lemmas first (distinct in-range lemma ids, pos filter, `ORDER BY rank DESC, value ASC LIMIT 10`), then computes `has_exact` (point PK probe) and the translations `GROUP_CONCAT` for those ten only.
- Bind parameter order becomes range-start, range-end, exact-form; the `completions` call site follows.
- Result columns, filter, ordering, and limit are unchanged; sqlite3 output diffs are byte-identical for f, fe, fen, fenster, sch, a, zu, ue.

## Impact

Modified: `sqlite-dictionary-worker`. Files: `src/client/adapters/dictionary.cljs`. Measured native: `f` 124 ms → ~22 ms, `fe` ~15 ms → ~4 ms, `fenster` unchanged at ≤1 ms.
