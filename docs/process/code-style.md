# Clojure Code Style

## Basic Rules

We mostly follow the [Clojure Style Guide](https://guide.clojure.style/) with the additions listed below.

## Additional Rules

### Namespace Aliases

Every required namespace **MUST** have an alias. **DO NOT** use `:refer`. Use the last segment of the required namespace as the alias.
**EXCEPTION**: When the last segment of the namespace is `core`, use the segment before it as the alias.
**EXCEPTION**: use standard aliases from basic rules.

**Example**

Bad
```clojure
(ns some-ns
 (:require
  [my-project.utils :refer [ensure-coll]]))
```

Good
```clojure
(ns some-ns
 (:require
  [my-project.utils :as utils]]))
```

Good
```clojure
(ns some-ns
 (:require
  [my-project.db.core :as db]]))
```


### Placement of function arguments

Function arguments **MUST** be placed on the next line after the function name. This simplifies git diffs when you decide to add a docstring to the function.

**Example**

Bad
```clojure
(defn some-function [x]
 (inc x))
```

Good
```clojure
(defn some-function
 [x]
 (inc x))
```


### Function Names

Names of pure functions **SHOULD** describe the produced value (`learning-progress`, `current-challenge`, etc.). This clarifies intent and keeps naming precise.
It helps avoid vague names like `process-data` or `prepare-data`, which usually signal unclear understanding of the function's purpose.

**Example**

Bad
```clojure
(defn calcalate-learning-progress
  [word-reviews]
  ;; function body
  )
```

Bad
```clojure
(defn process-word-reviews
  [word-reviews]
  ;; function body
  )
```

Good
```clojure
(defn learning-progress
  [word-reviews]
  ;; function body
  )
```

### Threading Form Placement

Prefer keeping the full pipeline inside the threading macro.
This rule applies to `->`, `->>`, `some->`, and `some->>`.

Bad
```clojure
(fn-3 (-> value fn-1 fn-2))
```

Good
```clojure
(-> value fn-1 fn-2 fn-3)
```

### Top-level Spacing

Separate top-level forms with **EXACTLY** two empty lines. This makes form visually distinguishable.

Bad
```clojure
(defn fn-1 [] ,,,)

(defn fn-2 [] ,,,)
```

Good
```clojure
(defn fn-1 [] ,,,)


(defn fn-2 [] ,,,)
```


### Map Key Sorting

Map keys **SHOULD** be sorted alphabetically, with `:id` and `:_id` keys always first. This provides consistent key ordering across the codebase.

**Example**

Bad
```clojure
{:name "test"
 :id 1
 :value 42
 :active true}
```

Good
```clojure
{:id 1
 :active true
 :name "test"
 :value 42}
```


### Map and Binding Alignment

Map values and let binding values **SHOULD** be aligned when keys/names have similar lengths. Alignment is disabled when the length variance exceeds 15 characters to avoid excessive whitespace.

**Example**

Good — similar key lengths, aligned
```clojure
{:id    1
 :name  "test"
 :value 42}
```

Good — similar binding lengths, aligned
```clojure
(let [x      1
      longer 2
      y      3]
  body)
```

Good — long key, no alignment
```clojure
{:id 1
 :name "test"
 :this-is-a-very-long-key-name "value"}
```


### No Commas in Maps

Do **NOT** use commas to separate map key-value pairs. Commas are whitespace in Clojure and add visual noise. While commas can aid readability in single-line maps, zprint cannot selectively apply them, so we omit them everywhere for consistency.

**Example**

Bad
```clojure
{:id 1, :name "test", :value 42}
```

Good
```clojure
{:id 1 :name "test" :value 42}
```


### Hiccup Layout

A hiccup form puts the tag, the attribute map and each child on a line of its own, under the tag, when it has an attribute map and children, when its lone child is a vector or a form, or when it takes more than one line. Otherwise — attributes alone, plain values as children — it stays on one line while it fits. An attribute map of more than one key puts each key on a line of its own, and so takes the form to several lines.

zprint enforces it (`.zprint.edn`, `:vector`). A vector counts as hiccup when its tag has a class or an id (`:div.card`, `:footer#home`) or is a bare HTML or SVG element (`:span`, `:path`). Key paths like `[:cards (get-in …)]`, Malli schemas and `[:i18n …]` do not.

**Example**

Bad — the tag carries the attributes and the first child
```clojure
[:button.tile__target {:type "button"} [:span.tile__name name]
 [:span.tile__count count]]
```

Bad — attributes and a child on one line
```clojure
[:p.home__error {:role "alert"} [:i18n :home/save-failed]]
```

Bad — attribute keys on one line
```clojure
[:line {:x1 "12" :y1 "2" :x2 "12" :y2 "15"}]
```

Good
```clojure
[:line
 {:x1 "12"
  :y1 "2"
  :x2 "12"
  :y2 "15"}]

[:button.tile__target
 {:type "button"}
 [:span.tile__name name]
 [:span.tile__count count]]

[:p.home__error
 {:role "alert"}
 [:i18n :home/save-failed]]

[:li
 [:i18n :home/words]]
```

Good — one attribute only, or plain values as children
```clojure
[:meta {:charset "UTF-8"}]
[:span.tile__count count]
```

A table of shapes reads better a row per element. A `;; zprint: as written` comment right before a form leaves every vector in it as written. A whole top-level form is skipped with zprint's own `;!zprint {:format :skip}`.

```clojure
;; zprint: as written
[:svg.splash-face {:viewBox "0 0 156 156" :width "156" :height "156"}
 [:circle.dot {:cx 55 :cy 32 :r 9 :fill "hsl(100 65% 72%)" :style "animation-delay:0s"}]
 [:circle.dot {:cx 101 :cy 32 :r 9 :fill "hsl(89 69% 71%)" :style "animation-delay:.15s"}]]
```
