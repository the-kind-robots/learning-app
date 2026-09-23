## Context

The alphabetical word list came in with #317. It reads a page off the `vocab-preview`
PouchDB view with `:limit`/`:skip`, and the view emits `doc._id` as its key — so the id is
the order. ADR-0008 makes that id `"vocab:" + normalize(value)` and freezes `normalize`:
the id is what two devices converge on, and changing it re-duplicates every word already
synchronised. `normalize` keeps the article, so `der Zug` is stored as `vocab:der zug` and
the list files it under D.

The behaviour this change settles is stated in
`specs/vocabulary-list-paging/spec.md` of this change, and nowhere else here.

## Goals / Non-Goals

**Goals:**

- One ordering key for the alphabetical list, used by the view that pages it and by the
  use case that sorts the scoped and searched variants, so all four paths agree.

**Non-Goals:**

- Touching `normalize-german` or the document id. ADR-0008 is in force and names articles
  as part of the frozen contract.
- German dictionary collation, or anything else the existing requirement already excludes.
- The lesson's order, which ranks by urgency and reads no alphabet.

## Decisions

**The key is emitted, not sorted afterwards.** The list is paged with `:limit`/`:skip` off
the view, so a page must arrive in the reader's order; a presenter sorting 50 rows would
order each page within itself and scramble the list across pages. Rejected for that reason.

**The key is derived from the id, not stored on the document.** Everything the key needs
is already in the id — it is the normalised value — so the map function can compute it and
no document is rewritten, no migration runs, and a document arriving by replication from a
device on the old code indexes correctly on arrival. A stored `sort_key` field would have
needed a pass over every vocab document and a second one for anything that syncs in later.

**The key is a pair, `[filed-under, id]`.** The article-less form alone collides — `der
Zug` and a bare `Zug` both give `zug`. PouchDB would break that tie by document id anyway,
but then the use case's own sorts would have to reproduce an implicit engine rule to stay
in step. The id in the key makes the total order explicit and lets `previews` keep asking
the view for exact keys.

**`domain.vocabulary` owns the key.** It already owns the other direction, `vocab-id`; the
two belong together. The map function is JavaScript inside the design document and so
states the same rule a second time, in the one place the language forces a copy — the test
for it asserts the two agree on the issue's words rather than on the string.

## Risks / Trade-offs

- [The stored design document changes, so PouchDB rebuilds the index on the next query] →
  `ensure-design-doc!` already rewrites a changed map on start, so the rebuild happens once
  per installation, in the background of the first words-screen read. Measured; the numbers
  are in the tasks' verification note, and the key's shape is not what they are made of.
  Nothing is lost if it is interrupted — the index is derived, and the next query resumes
  it.
- [A word whose value legitimately begins with an article-shaped token is refiled] → Only
  an exact `der `/`die `/`das ` prefix is dropped, so `dasselbe` and `Diebstahl` keep their
  first letter. A phrase that opens with an article files under its next word; that is the
  same rule and no worse than filing every one of them under D.

## Migration Plan

None. No document changes. The index rebuild is the whole of it and it is automatic.
Rolling back restores the previous map function, which `ensure-design-doc!` writes back on
the next start and which rebuilds the index the same way.

## Open Questions

None.
