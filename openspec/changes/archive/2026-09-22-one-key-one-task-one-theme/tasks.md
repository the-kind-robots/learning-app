# Tasks

## 1. The key covers the prompt

- [x] 1.1 `examples/generation-version` carries what the dictionary said about the word, `nil`
      included, and one reading of the dictionary serves the prompt and the key.
- [x] 1.2 `without-dictionary-meta`, `cacheable?` and the route's gate are gone with the rule they
      served.
- [x] 1.3 Tests: the answer given while the dictionary was away is kept; the word arriving in the
      dictionary is a miss.

## 2. A fetch is one task per pair

- [x] 2.1 `tasks/create-task` takes the id its caller composes; `create-tasks!` writes by it.
- [x] 2.2 `adapters.examples` composes `task:example-fetch:<word-id>:<collection-id>`.
- [x] 2.3 A dead letter moves to `<id>:failed`, leaving the pair askable.
- [x] 2.4 `pending-requests`, its port, the `by-type-task-type` index, the public `tasks/alive`, the
      `pending` argument and the chaining atom in `start!` are gone.
- [x] 2.5 Tests: asking twice writes once; a dead-lettered pair can be asked for again.

## 3. A theme a pass brings is unfolded

- [x] 3.1 `entries-of-pass` adds the entries named by a collection among the pulled ids, recognised
      by id against the collections this device holds.
- [x] 3.2 Tests: a pass carrying only a theme queues the theme's missing pairs.

## 4. Delivery

- [x] 4.1 Backend, client node and browser suites run.
- [ ] 4.2 Sync the specs and archive the change on this branch.
