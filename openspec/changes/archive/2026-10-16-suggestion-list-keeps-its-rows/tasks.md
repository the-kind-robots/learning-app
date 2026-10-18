## 1. Specs

- [x] 1.1 Replace "a dropped row is not on screen" with inert rows.

## 2. Implementation

- [x] 2.1 `narrowed` keeps all rows and moves the marked entry to the first completing one.
- [x] 2.2 Keydown, add-word and mode read only completing rows; presenter flags `:completes?`; view renders inert rows.

## 3. Verify

- [x] 3.1 Browser spec: the row count holds on every keystroke; an inert row cannot be picked.
