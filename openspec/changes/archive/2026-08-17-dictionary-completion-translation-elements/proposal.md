## Why

The completions query joins a lemma's translations into one string with
`GROUP_CONCAT`, and the client splits that string on `,`. The separator is
indistinguishable from the content: 1100 of the dictionary's 301 683 translations
contain a comma themselves — including frequent lemmas such as `ohne`,
`eigentlich`, `unten` and `indem`. For `indem`, three translations arrive as five
fragments, two of them the bare word ` что`.

Transport, not input, is the defect: whatever the suggestion carries is what the
add-word form prefills and what gets stored, so a fragment entered once is a
fragment forever.

## What Changes

- The completions SQL returns each lemma's translations as a JSON array
  (`json_group_array`) instead of a comma-joined string.
- The dictionary adapter parses that array and passes the elements through. It
  no longer splits anything.
- A lemma with no translations yields an empty sequence, where the old split of
  `""` yielded a one-element sequence containing the empty string.
- Values arrive verbatim: no lost commas, no leading space left by the split.

Not a breaking change for callers — `:translations` was already a sequence of
strings and stays one.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `sqlite-dictionary-worker`: the completion result requirement gains an explicit
  guarantee that a translation containing the separator survives transport intact,
  and the existing cost requirement is restated so the fix cannot be traded for
  the short-prefix regression #179 removed.

## Impact

- `src/client/adapters/dictionary.cljs` — the SQL and the row decoding.
- Requires SQLite with JSON1; the vendored WASM build (3.53.1) has it.
- No change to the dictionary file, its schema, or the build pipeline.
- No change to `domain/vocabulary.cljs` or the add-word form; the input half is
  settled separately in #365.
