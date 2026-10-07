## Why

Typing a word in full does not put it first (#357): `rücken` shows der Rücken tenth of ten,
after its compounds. Two causes. The frequency file keys SUBTLEX rows by `normalize-german`
and lets the last, rarest spelling of a key win, so Rücken carries the rank of `ruecken`,
für that of `fuer`, über that of `ueber` — 427 keys, every one wrong the same way. And the
completion query orders by rank alone, so a word the user has finished typing competes with
its own compounds and can fall out of the top ten altogether.

## What Changes

- The frequency index keeps the best rank of a normalized key, whichever spelling earned it.
  The dictionary is rebuilt from that index.
- Every lemma that matches the typed word exactly — all parts of speech — comes first,
  ordered by rank among themselves, and is in the list even when it is outside the rank top
  ten. The rest of the list follows by rank.
- The translation prefill is unchanged: it takes the first row, which is now the exact match
  when there is one.
- Query cost stays bounded by the result: the exact matches are a point lookup on the
  surface-form key, measured against the shipped dictionary on short prefixes.

## Capabilities

### New Capabilities

### Modified Capabilities

- `sqlite-dictionary-worker`: the completion result puts exact matches first and always
  includes them; the cost requirement names the exact lookup.
- `dictionary-storage`: the frequency index merges spellings of one normalized key, keeping
  the best rank.

## Impact

`tools/dictionary/dictionary/frequency.clj` and its test; `resources/dictionary/*` rebuilt;
`src/client/adapters/dictionary.cljs` (the completions SQL) and its unit test. No API,
schema or dependency change.
