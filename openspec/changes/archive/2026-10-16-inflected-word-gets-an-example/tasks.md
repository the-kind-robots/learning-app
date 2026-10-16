## 1. Specs

- [x] 1.1 Modify the phrase-construction requirement: presence by lemma.

## 2. Implementation

- [x] 2.1 `dictionary/lemma-ids`; `target-present?` accepts a shared lemma.
- [x] 2.2 Prompt carries `lemma`; retry message states the rule.

## 3. Verify

- [x] 3.1 Unit cases over a built fixture dictionary and an HTTP-edge case: `clojure -M:test`.
