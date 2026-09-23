## 1. The key

- [x] 1.1 `domain.vocabulary`: the key a word is filed under, derived from its id.
- [x] 1.2 Node test: the issue's six words sort into the order the delta spec names, and
      `dasselbe` keeps its first letter.

## 2. The list reads it

- [x] 2.1 `adapters.words`: the `vocab-preview` map emits the key; `previews` asks the view
      for the keys of the ids it was given.
- [x] 2.2 `use-cases.vocabulary`: the three id sorts in `alphabetical` — the searched page,
      the scoped page's cut, and the final sort — use the key.
- [x] 2.3 Browser test: the view and the domain key agree on the issue's six words.

## 3. Verification

- [x] 3.1 Browser spec: the list shows the six words in order, a page past the first
      continues it, and editing a word by value still finds the same entry. Screenshot.
- [x] 3.2 Measure what the first query costs after the map function changed.

### What the index rebuild costs

Headless Chrome under WSL, PouchDB on IndexedDB, a fresh browser context per run, three
runs each. The design document is rewritten with the new map and the next `:limit 50` query
is timed — what `ensure-design-doc!` sets up on the first start after the update.

| Words | First query after the map changed | Same query, warm |
|------:|----------------------------------:|-----------------:|
|   500 |                       0.42–0.63 s |          12–15 ms |
|  1000 |                       0.73–1.39 s |          11–28 ms |
|  2000 |                       2.57–2.65 s |          12–15 ms |
|  5000 |                            6–15 s |          11–24 ms |

It is one index build, paid once, and the new key is not what makes it cost. Building the
same view over the same 5000 documents, each key shape in a context of its own: the old
`doc._id` 7.0/10.4/9.2 s, the article-stripped string 6.1/6.3/6.5 s, the `[key id]` pair
this change emits 6.8/7.1/8.1 s — one spread, no shape apart. The 12–15 s readings above
came from runs that had already built the old index in the same page.
