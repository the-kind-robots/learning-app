## Context

Completions come from one SQL statement in `adapters/dictionary.cljs`: a `DISTINCT lemma_id`
over the `surface_forms` prefix range, joined to `lemmas`, ordered by rank, cut to ten, then
translations and a per-row `has_exact` point lookup for the survivors (#179, #362). Rank comes
from the build: `transform/compute-rank` takes `1000000 - <SUBTLEX position>` when the frequency
index has the word. The index (`dictionary/frequency.clj`) is built with `(into {} …)` over rows
sorted by count, keyed by `normalize-german`, so for a key with several spellings the last row —
the rarest — wins. ADR-0008 freezes `normalize-german` as an id contract; nothing here touches it.

## Goals / Non-Goals

**Goals:**
- A key's rank is its best spelling's rank.
- Exact matches first, all of them, always present; the rest by rank; ten rows in all.
- Short prefixes cost what they cost today.

**Non-Goals:**
- Reworking how short prefixes are answered (#495).
- Changing the range bound `prefix + "z"` (#495).
- Changing the prefill rule: it stays "first row".

## Decisions

- **Merge in `read-frequency-file`, by keeping the first row per key.** Rows are already sorted
  by count descending (or carry a rank), so the first occurrence of a key is its best. A
  `reduce` that keeps an existing entry replaces `(into {} …)`. Alternative: summing counts
  across spellings — rejected; `transform/frequency-for-entry` already sums counts across the
  candidates it matches, and the owner asked for the best, not a sum.
- **Union, not re-sort.** The query takes the exact matches by equality on `normalized_form`
  (leading PK column of `surface_forms`, a point lookup), the rank top ten of the range
  excluding those ids, unions them, and orders by `exact DESC, rank DESC, value ASC LIMIT 10`.
  Re-sorting within the top ten would still lose an exact match ranked eleventh. Dropping the
  limit for exact matches is harmless: a form belongs to a handful of lemmas at most.
- **`has_exact` comes from the union flag.** The per-row `EXISTS` is removed; the flag is
  known from which arm produced the row. One fewer lookup per row.
- **The dictionary is rebuilt, not patched.** `clojure -X:dictionary build` from the repository
  root reproduces every artifact in `resources/dictionary` from the fixed index; patching ranks
  in place would leave `enrichment-meta.jsonl` and the manifest disagreeing with the file. The
  `-T` form the READMEs gave drops the project paths, so `utils` is not on the classpath and
  the build fails before reading a line; the READMEs are corrected.

## Risks / Trade-offs

- [The exact CTE is evaluated twice — in the union and in the `NOT IN`] → it is a handful of
  rows on a clustered key; measured on the shipped dictionary before and after.
- [The rebuild changes ranks for every lemma whose key collided, not only the five named] →
  that is the fix; the diff is reported as a count of changed ranks and the five are spot-checked.
- [A rebuild on another machine downloads Kaikki anew and may differ in content] → the local
  build reuses the Kaikki dump and Goethe list already on disk; only the frequency merge changes.

## Migration Plan

Ship the rebuilt `resources/dictionary/*` with the code. The deploy is the owner's
`Deploy Dictionary` workflow, run separately; nothing here deploys.

## Open Questions

None.
