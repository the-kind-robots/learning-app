# 0013. Collection folders are a naming convention; the parent's scope is computed on read

- Status: accepted
- Date: 2026-09-13

## Context

The themes screen (GH-406) groups collections into folders: `Kurs / Kapitel 1`
and `Kurs / Kapitel 2` are one tile with a header `Kurs`, and a lesson on
`Kurs` draws from both chapters. Two shapes were possible: a `parent-id` on
the collection document, or the `/` in the name as the whole structure.

A `parent-id` needs a migration, a move flow, and a way to keep it in step
with a rename; the name the user typed already says where the collection
belongs, and moving a collection is then a rename. A stored union on the
parent (`word-ids` copied up) would need a write on every add to a child
and would drift on every sync conflict.

## Decision

A folder is the text before the first `/` in a collection's name, trimmed,
compared case-insensitively. The parent of a folder is the collection whose
whole name equals that text by the same comparison; it is an ordinary
collection and may not exist.

The scope of a collection — the words a lesson on it draws from, the words
its list shows, the count its tile carries — is the distinct union of its own
`word-ids` and the `word-ids` of every collection whose folder key equals its
name by that comparison. Nesting is one level: `Kurs / A / B` belongs to
`Kurs`, not to `Kurs / A`. It is computed on read by
`use-cases.collections/scope-word-ids` and nowhere else.
Documents hold only their own `word-ids`. Removing a word from the parent
edits the parent document only.

## Consequences

- No migration and no new field; existing names with `/` become folders on
  the next open of the screen.
- Adding a word to a child is one write and the parent shows it at once.
- A word removed from the parent stays in the child and therefore in the
  parent's union; dropping it from the union means removing it from the
  child.
- Nesting is one level on screen: deeper `/` stay in the child's row text.
  Folders as documents, if ever needed, supersede this record.
