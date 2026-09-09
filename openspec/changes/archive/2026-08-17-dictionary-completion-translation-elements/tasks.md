## 1. Transport

- [x] 1.1 Replace `GROUP_CONCAT(DISTINCT t.value ORDER BY t.rank ASC)` in `completions-sql` with `json_group_array(DISTINCT t.value ORDER BY t.rank ASC)`
- [x] 1.2 Update the query's docstring: record why the separator was ambiguous and that the #179 shape is unchanged
- [x] 1.3 Decode the JSON array in `adapters.dictionary/completions` and drop the `str/split`, including the now-unused `clojure.string` require

## 2. Tests

- [x] 2.1 Add a node test over the row-decoding step, covering a translation containing a comma, order preservation, and a lemma with no translations
- [x] 2.2 Run `npx shadow-cljs compile node-test && node target/node-tests.js` and confirm the existing home suggestion tests still pass

## 3. Verification

- [x] 3.1 Run the new SQL against the shipped 37 MB dictionary in the real WASM/OPFS environment and diff old against new over whole result sets, confirming that only the damaged rows change
- [x] 3.2 Measure medians over at least 21 repetitions for prefixes `f`, `fe`, `fen`, `sch`, `hau`, `a` before and after, and record them in `design.md`
