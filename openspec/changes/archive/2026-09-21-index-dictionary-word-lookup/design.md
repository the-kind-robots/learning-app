## Context

The behaviour this change owes is stated once, in `specs/examples/spec.md` of this change. This
document only records how it is met and why the alternatives were rejected.

Server-side, the dictionary lives in a CouchDB database of ~850k documents, read by
`src/backend/examples/dictionary.clj`. The lookup selects on `type` and `meta.normalized_value`.
Nothing in the repository creates a CouchDB index — `grep -rn "_index"` finds one docstring and no
call — so the selector is answered by the `_all_docs` special index, which is a full read.

Two paths reach a dictionary entry. The `_find` above, and a surface-form document read by id
(`sf:<word>`) followed by `_all_docs` with explicit keys. Only the first is unindexed.

The ADRs in force concern the client-side SQLite/OPFS dictionary; none constrains the server-side
CouchDB copy.

## Goals / Non-Goals

**Goals:**

- The word lookup answers from an index.
- A database loaded from scratch becomes queryable without a manual step.

**Non-Goals:**

- Changing the lookup's selector, its normalization, or the surface-form path.
- Changing what the prompt does with the two fields once it has them.
- The duplicated `import.sh` (#329) and the `:dictionary-import` alias, whose namespace is no longer
  in the tree — both are out of scope and untouched.

## Decisions

**A composite JSON index over `["type", "meta.normalized_value"]`.** The selector constrains both
fields by equality, so the composite index turns the lookup into a key range. The alternative — a
single-field index over `meta.normalized_value` with a partial filter on `type` — is not chosen
automatically by CouchDB's query planner; it has to be named by the query with `use_index`, which
puts the index's name in the call site and breaks the lookup if the two ever disagree.

**Created by the backend at boot, not by a provisioning script.** The three shell scripts that
create the database (dev stand installer, package postinst, admin setup) would each need the same
JSON, and a reset-and-reimport run between two of their invocations would drop the index without
bringing it back. The backend is the index's only reader and starts after every deploy, so it is the
one place that cannot drift from the query. It is created beside the query it serves, in
`examples/dictionary.clj`.

**Best-effort at boot, like the reconciliation report.** `serve!` already treats an unreachable
CouchDB as a warning rather than a failed boot; index creation joins that step under the same rule.
An app that cannot generate examples is still an app that serves.

## Risks / Trade-offs

- **CouchDB builds a Mango index lazily, on the first query against it, and that build can outlast
  the query's own timeout** → after a from-scratch load the first lookup may still time out once and
  degrade to the unknown-word path, exactly as before the fix; the build continues in the background
  and every later lookup is answered from the index. Measured locally: the build ran under a minute
  over ~850k documents. Not worth a boot-time warm-up, which would trade a rare single slow request
  for a slow boot on every start.
- **The index covers every document in the database, not only dictionary entries** → additional
  on-disk index, proportional to the document count. Accepted: a partial index would have to be named
  at the call site (see Decisions).
