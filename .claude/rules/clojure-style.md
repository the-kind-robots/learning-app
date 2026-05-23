---
paths:
  - "**/*.clj"
  - "**/*.cljs"
  - "**/*.cljc"
  - "**/*.edn"
---

# Clojure Style

Follow https://guide.clojure.style/ plus these project rules:

**Namespace aliases:** always alias, never `:refer`. Last segment is alias; if last segment is `core`, use the one before it.

**Function args:** always on their own line after the name.

**Function names:** pure functions named after the value they return (`learning-progress`, not `calculate-learning-progress` or `process-word-reviews`).

**Threading:** keep the full pipeline inside the threading macro — no wrapping the result with an outer call.

**Top-level forms:** exactly two blank lines between them.

**Maps:** keys sorted alphabetically with `:id`/`:_id` first; align values when key length variance ≤ 15 chars; no commas.
