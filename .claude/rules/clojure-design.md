---
paths:
  - "**/*.clj"
  - "**/*.cljs"
  - "**/*.cljc"
---

# Clojure Design

**Before adding a function or a map key**, run LSP `findReferences` / `incomingCalls` on what it wraps or carries and answer two questions: who creates the value, who reads it. If the answer is "one place creates it, the next line reads it", the new name is not needed.

Smells to name when you see them:

- **Feature envy / tell, don't ask** — a function reads fields of another value to decide for it. Move the logic to where the data lives, or pass the whole value.
- **Tramp data / pass-through variable** — an argument travels through functions that only hand it on. Give it to the one that reads it.
- **Middle man** — a function that only forwards to another. Call the other one.
- **Speculative generality** — an option, argument or layer for a case that has not happened. Remove it until the case is real.

Project rules that follow from these:

- **Wandering data.** A value is assembled and taken apart by field right away (`(let [m {:a a :b b}] (f (:a m) (:b m)))`). Before building it, ask whose knowledge it is; pass the parts or the owner.
- **Fewer functions.** Express new code through what exists. A private helper or `def` with one call site, or a second name for an existing key, is inlined. `bb scripts/single_caller.clj [path ...]` lists them.
- **An object handed its own field.** `(f m (:k m))` — pass `m`, read `:k` inside `f`. `bb scripts/field_of_same_map.clj [path ...]` lists these.
