(ns i18n
  "The interface's text. Markup says `[:i18n :key params]` where text goes; `interpolate` puts the
   active language's text there. A language is one dictionary, `i18n/<language>.edn`."
  (:require
   #?(:clj [clojure.edn :as edn])
   #?(:clj [clojure.java.io :as io])
   #?(:cljs [cljs.reader :as reader])
   #?(:cljs [shadow.resource :as rc])
   [m1p.core :as m1p]))


(def language
  "The language the interface speaks, as `<html lang>` names it."
  "ru")


#?(:clj
   (defmacro ^:private inline-edn
     "The EDN at `path` on the classpath, read when the code compiles: a jar carries no source files."
     [path]
     (list 'quote (edn/read-string (slurp (io/resource path))))))


(def ^:private dictionary
  (m1p/prepare-dictionary
   #?(:clj (inline-edn "i18n/ru.edn")
      :cljs (reader/read-string (rc/inline "/i18n/ru.edn")))))


(defn interpolate
  "`markup` with the text of the active language in place of every `[:i18n :key params]`.
   A vector or map without one is returned as it came, so a render copies only what holds text."
  [markup]
  ;; Not `m1p/interpolate`: its postwalk rebuilds every node, several times this walk on a long word list (ADR-0025).
  (cond
    (and (vector? markup) (= :i18n (first markup)))
    (let [[_ k params] markup]
      (m1p/lookup nil dictionary k params))

    (or (vector? markup) (map? markup))
    (reduce-kv (fn [coll k v]
                 (let [v' (interpolate v)]
                   (if (identical? v v') coll (assoc coll k v'))))
               markup
               markup)

    (seq? markup)
    (doall (map interpolate markup))

    :else
    markup))
