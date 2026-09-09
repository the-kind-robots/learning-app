## Context

`completions-sql` aggregates a lemma's translations with
`GROUP_CONCAT(DISTINCT t.value ORDER BY t.rank ASC)` and the adapter splits the
result on `,`. The default separator is also ordinary content: 1100 of 301 683
translations contain a comma. Three translations of `indem` arrive as five
fragments.

The shape of this query is load-bearing. ADR-0002 puts the dictionary behind a
worker, so every completion is one round trip; #179 rewrote the query so only ten
surviving lemmas ever reach the translation lookup, which took a one-letter prefix
from ~100 ms to ~25 ms. Any fix here touches exactly that lookup and must not
trade the win back.

In-force ADRs consulted: 0001, 0002 (dictionary in SQLite/OPFS behind a worker),
0003, 0005, 0008 (`normalize-german` is a frozen id contract — untouched here),
0011. None constrain the choice below beyond "the query stays in the worker".

## Goals / Non-Goals

**Goals:**

- A translation containing any punctuation survives transport as one element.
- The client stops splitting; it receives elements and passes them on.
- The completion cost stays where #179 left it.

**Non-Goals:**

- The input path. What the user types is settled in #365; `domain/vocabulary.cljs`
  and the add-word form are untouched here.
- Rebuilding the dictionary file, changing its schema, or the build pipeline.
- The `", "` join the home actions use to prefill the form field. That join is on
  the input side of the boundary and belongs to #365.

## Decisions

**Decision: carry the translations as a JSON array (`json_group_array`).**

```sql
(SELECT json_group_array(DISTINCT t.value ORDER BY t.rank ASC)
 FROM translations t
 WHERE t.lemma_id = top.id) AS translations
```

The adapter parses it with `JSON.parse` and passes the elements on.

Alternatives considered:

1. *A different separator inline* — `GROUP_CONCAT(DISTINCT value, char(31))`.
   Rejected: SQLite refuses it outright, *"DISTINCT aggregates must have exactly
   one argument"*. A custom separator and `DISTINCT` cannot be combined.

2. *A different separator via a subquery* — dedupe in an inner `SELECT`, then
   `GROUP_CONCAT(v, char(31))`. Works, and `char(31)` appears zero times across
   all 301 683 translations today. Rejected on two counts. It reserves a
   character, and nothing in the build pipeline enforces that reservation, so the
   claim has to be re-verified on every dictionary rebuild — an invariant held by
   nobody. And it measured as the most expensive of the three: the dedupe subquery
   costs +31 % on the aggregate against +8 % for JSON (below).

3. *JSON array* — chosen. Structure travels as structure. There is no character
   to reserve, so no invariant survives past this change and no rebuild can
   silently break it. JSON1 is compiled into the vendored WASM build (3.53.1,
   verified by running the query against the shipped dictionary).

**Why measurement, not taste.** Two runs against the shipped 37 MB dictionary in
the real environment — Chrome, the vendored SQLite 3.53.1 WASM, its own
`opfs-sahpool` VFS, the dictionary imported into OPFS. Variants interleaved inside
each repetition so drift hits all three equally; medians of 61 repetitions.

| prefix | `GROUP_CONCAT` (before) | `char(31)` + subquery | `json_group_array` (after) |
|--------|------------------------:|----------------------:|---------------------------:|
| `f`    | 47.5 ms                 | 48.0 ms               | 45.4 ms                    |
| `fe`   | 7.5 ms                  | 7.7 ms                | 7.6 ms                     |
| `fen`  | 1.5 ms                  | 1.5 ms                | 1.6 ms                     |
| `sch`  | 31.0 ms                 | 31.2 ms               | 30.7 ms                    |
| `hau`  | 4.0 ms                  | 4.6 ms                | 4.4 ms                     |
| `a`    | 83.2 ms                 | 78.9 ms               | 82.8 ms                    |

End to end the three are indistinguishable: every difference is smaller than the
run-to-run spread, and the sign is not stable between the two runs. That is the
expected result — the aggregate runs for ten lemmas while the prefix range scan,
identical in all three, dominates.

Isolating the aggregate for a fixed set of ten lemma ids (batches of 200 execs,
median of 25 batches) separates it from that noise:

| variant | per call | vs before |
|---------|---------:|----------:|
| `GROUP_CONCAT` | 0.329 ms | — |
| `char(31)` + subquery | 0.429 ms | +30 % |
| `json_group_array` | 0.354 ms | +8 % |

+0.025 ms per completions call — 0.03 % of the `a` case. The #179 property is
untouched, and the rejected alternative is the expensive one.

**What the fix actually recovers.** The shipped SQL string, extracted from
`adapters/dictionary.cljs` and run against the shipped dictionary through the real
WASM build and OPFS pool, compared row for row against the old query decoded the
old way. Over the prefixes `unt`, `eigentl` and `hos`: 23 rows, 19 byte-identical,
0 reordered, 4 repaired.

| lemma | before | after |
|---|---|---|
| `unten` | `внизу` / ` снизу` / `ниже` | `внизу, снизу` / `ниже` |
| `eigentlich` | `действительный` / ` настоящий` / `собственный` | `действительный, настоящий` / `собственный` |
| `die Hose` | `брюки` / `штаны` / `смерч ` / ` (небольшое) торнадо` | `брюки` / `штаны` / `смерч , (небольшое) торнадо` |
| `unterhalten` | one blank translation | none |

**Decision: the adapter stops splitting entirely.** `(str/split "" #",")` returns
`[""]`, so a lemma with no translations produced one blank translation; a parsed
`[]` produces none. The split also left a leading space on every fragment after
the first, which nothing trimmed.

## Risks / Trade-offs

- **JSON1 missing from a future SQLite build** → the query fails at runtime rather
  than degrading. Mitigated by the vendored build being pinned in `package.json`
  and fetched by `npm run fetch-sqlite`; JSON1 has been compiled in by default
  since 3.38.
- **A malformed JSON value** → `JSON.parse` throws inside the completions promise.
  Not reachable through `json_group_array`, which emits well-formed JSON by
  construction; the values themselves are escaped by SQLite.
- **The measured absolute numbers do not match #179's 20-27 ms for `f`.** They
  were taken on different hardware and with a live app tab in the same browser.
  Only the before/after within one run is comparable, and that is how the table
  above is read.
- **Downstream still re-joins.** The home actions prefill the form with
  `(str/join ", " translations)`, and `parse-translations` splits that again on
  `[,;.]`. This change makes the dictionary side honest; the round trip through
  the form is #365's subject, and until it lands a comma-carrying translation is
  still damaged after the user's edit — but no longer before it.

## Migration Plan

No data migration. The dictionary file, its schema and the manifest are unchanged,
so old and new clients read the same artifact. Rollback is reverting the commit.

## Open Questions

None. No in-force ADR needs revisiting.
