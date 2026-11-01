---
paths:
  - "**/*.clj"
  - "**/*.cljs"
  - "**/*.cljc"
  - "**/*.edn"
---

# Clojure Style

Follow https://guide.clojure.style/ plus these project rules:

**Namespace aliases:** always alias, never `:refer`. The alias is the last segment that names a subject: generic segments are dropped, and the one before is taken — `domain.lesson` → `lesson`, `foo.core` → `foo`, `pages.lesson.effects` → `lesson`. Generic segments: `core`, `domain`, `use-cases`, `ports`, `adapters`, `pages`, `effects`, `actions`, `presenter`, `view`. Two requires that land on one alias go to the owner.

**Names:** a thing is called what it is — `reviewed-at` and `review-id`, not `seconds` and `id`; `insert-position`, not `at` or `index`. No one-letter or abbreviated names outside a formula's own notation, and none there when the formula is ported: `weights`, `stability`, not `w`, `s`.

**Function args:** always on their own line after the name.

**Function names:** pure functions named after the value they return (`learning-progress`, not `calculate-learning-progress` or `process-word-reviews`). A rule of thumb, not a law: depart from it with a reason.

**Threading:** keep the full pipeline inside the threading macro — no wrapping the result with an outer call.

**Top-level forms:** exactly two blank lines between them.

**Maps:** keys sorted alphabetically with `:id`/`:_id` first; align values when key length variance ≤ 15 chars; no commas.
