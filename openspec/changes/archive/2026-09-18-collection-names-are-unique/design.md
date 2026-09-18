## Context

`use-cases.collections/create!` refuses a duplicate through `domain.collections/same-name?`, the equality ADR-0013 fixes for the folder lookup. `rename-active!` trims, clamps and writes; it never looks at the other collections. The home screen shows the rename's result by saving the returned name, which is how a blank edit reverts.

## Goals / Non-Goals

**Goals:**
- One name-equality for create, rename and the folder lookup; a taken name is refused on rename.
- The refusal shows the way the blank case does: the heading reverts, nothing is written.

**Non-Goals:**
- Repairing existing duplicates; merging documents.
- A message explaining the refusal.

## Decisions

- **Rename reads the list and excludes itself by id.** The collection's own name in another case is a rename. Alternative — comparing against the current name — would refuse `Kurs` → `KURS`.
- **The result keeps its shape.** `{:name current}` is what the effect saves; `:noop :duplicate` marks the refusal for callers and tests without a new path in the effect.
- **No new ADR.** ADR-0013 already names the equality; this change applies it to the one writer that skipped it.

## Risks / Trade-offs

- [A refusal with no message] → the heading reverting is the same signal a blank edit gives today; a message is a separate decision.
