---
paths:
  - "**/*.clj"
  - "**/*.cljs"
  - "**/*.cljc"
  - "**/*.edn"
---

# Clojure Style

Follow https://guide.clojure.style/ plus these project rules:

**Namespace aliases:** always alias, never `:refer`. The alias is the last segment that names a subject: generic segments are dropped, and the one before is taken — `domain.lesson` → `lesson`, `foo.core` → `foo`, `pages.lesson.effects` → `lesson`, `adapters.account` → `account` (in its own port too, never `adapter`; `bb scripts/check_adapter_layering.clj` checks it). Generic segments: `core`, `domain`, `use-cases`, `ports`, `adapters`, `pages`, `effects`, `actions`, `presenter`, `view`. When two requires land on one alias, the later one takes its last two segments: `[muuntaja.core :as muuntaja]` and `[reitit.http.interceptors.muuntaja :as interceptors.muuntaja]`.

**Names:** a thing is called what it is — `reviewed-at` and `review-id`, not `seconds` and `id`; `insert-position`, not `at` or `index`. No one-letter or abbreviated names outside a formula's own notation, and none there when the formula is ported: `weights`, `stability`, not `w`, `s`.

**Function args:** always on their own line after the name.

**Function names:** pure functions named after the value they return (`learning-progress`, not `calculate-learning-progress` or `process-word-reviews`). A rule of thumb, not a law: depart from it with a reason.

**Threading:** keep the full pipeline inside the threading macro — no wrapping the result with an outer call.

**Top-level forms:** exactly two blank lines between them.

**JS interop:** call a JS method on a `^js` parameter or local, or through `js-invoke` — never on a form of an inferred type (`(str …)`, a literal, a number), where `^js` on the form does not help: the release build renames a name Closure's externs lack, and only there it throws. [docs/dev/cljs-interop-renaming.md](../../docs/dev/cljs-interop-renaming.md); `bb scripts/release_renames.clj` checks it.

**Hiccup:** attributes and children, a lone child that is a vector or a form, or more than one line → the tag, the attribute map and each child on a line of their own; attributes alone or plain values stay on one line while they fit; an attribute map of several keys has a key per line. zprint enforces it; a `;; zprint: as written` comment before a form exempts it. [docs/process/code-style.md](../../docs/process/code-style.md), "Hiccup Layout".

**Maps:** keys sorted alphabetically with `:id`/`:_id` first; align values when key length variance ≤ 15 chars; no commas.
