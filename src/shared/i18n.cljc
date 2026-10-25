(ns i18n
  "The interface's text. Markup says `[:i18n :key params]` where text goes; `interpolate` puts the
   text of a language there. A language is one dictionary, `i18n/<language>.edn`: adding the file adds the
   language, its `:language/name` names it in the settings."
  #?(:cljs (:require-macros [i18n.files :as files]))
  (:require
   #?(:clj [clojure.edn :as edn])
   #?(:clj [i18n.files :as files])
   #?(:cljs [cljs.reader :as reader])
   [clojure.string :as str]
   [m1p.core :as m1p]))


(def ^:private dictionaries
  "Each language's dictionary, by its code."
  (update-vals (files/dictionary-texts)
               #(m1p/prepare-dictionary #?(:clj (edn/read-string %)
                                           :cljs (reader/read-string %)))))


(def languages
  "The languages the learner can pick, as `<html lang>` names them: those with a dictionary."
  (vec (sort (keys dictionaries))))


(defn language-name
  "`language` by its own name, as its dictionary gives it."
  [language]
  (m1p/lookup nil (dictionaries language) :language/name nil))


(defn- primary-subtag
  "The language of the tag `tag`, `de-DE` or `de`, without its region: `de`. Nil for no tag."
  [tag]
  (some-> tag str/lower-case (str/split #"-") first))


(defn system-language
  "The language a learner who picked none gets on a system that speaks `tag`, among `languages`: that one,
   else English, else Russian, which every build has."
  ([tag] (system-language languages tag))
  ([languages tag]
   (let [spoken   (set languages)
         language (primary-subtag tag)]
     (cond
       (spoken language) language
       (spoken "en")     "en"
       :else             "ru"))))


(defn language
  "The language the interface speaks: `picked`, while it has a dictionary, else the system's."
  [picked tag]
  (if (some #{picked} languages) picked (system-language tag)))


(defn asks-language?
  "Whether a system that speaks `tag` asks the learner to pick among `languages`: German is learned, never
   spoken by the interface, and there is something to pick."
  ([tag] (asks-language? languages tag))
  ([languages tag]
   (and (= "de" (primary-subtag tag)) (< 1 (count languages)))))


(defn- interpolated
  [dictionary markup]
  ;; Not `m1p/interpolate`: its postwalk rebuilds every node, several times this walk on a long word list (ADR-0025).
  (cond
    (and (vector? markup) (= :i18n (first markup)))
    (let [[_ k params] markup]
      ;; A key the language has not translated yet reads in Russian, so a dictionary can grow key by key.
      (m1p/lookup nil (if (contains? dictionary k) dictionary (dictionaries "ru")) k params))

    (or (vector? markup) (map? markup))
    (reduce-kv (fn [coll k v]
                 (let [v' (interpolated dictionary v)]
                   (if (identical? v v') coll (assoc coll k v'))))
               markup
               markup)

    (seq? markup)
    (doall (map #(interpolated dictionary %) markup))

    :else
    markup))


(defn interpolate
  "`markup` with the text of `language` in place of every `[:i18n :key params]`.
   A vector or map without one is returned as it came, so a render copies only what holds text."
  [language markup]
  (interpolated (dictionaries language) markup))
