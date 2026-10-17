(ns adapters.dictionary
  (:require
   [db.sqlite :as sqlite]
   [utils :as utils]))


(def ^:private completions-sql
  "Ten lemmas for a prefix: exact matches of the typed word first, then the rank
   top of the prefix range, each with its translations and its in-range forms as
   JSON arrays. Binds are range start, range end, exact form."
  "WITH ranged AS (
     SELECT lemma_id, json_group_array(normalized_form) AS forms
     FROM surface_forms
     WHERE normalized_form >= ? AND normalized_form <= ?
     GROUP BY lemma_id),
   exact AS (
     SELECT l.id, l.value, l.pos, l.rank, r.forms
     FROM (SELECT DISTINCT lemma_id
           FROM surface_forms
           WHERE normalized_form = ?) m
     JOIN lemmas l ON l.id = m.lemma_id
     JOIN ranged r ON r.lemma_id = m.lemma_id
     WHERE l.pos NOT IN ('conj', 'particle', 'pron', 'prep')),
   rest AS (
     SELECT l.id, l.value, l.pos, l.rank, r.forms
     FROM ranged r
     JOIN lemmas l ON l.id = r.lemma_id
     WHERE l.pos NOT IN ('conj', 'particle', 'pron', 'prep')
     ORDER BY l.rank DESC, l.value ASC
     LIMIT 10),
   top AS (
     SELECT id, value, pos, rank, forms, 1 AS has_exact FROM exact
     UNION ALL
     SELECT id, value, pos, rank, forms, 0 AS has_exact FROM rest
     WHERE id NOT IN (SELECT id FROM exact)
     ORDER BY has_exact DESC, rank DESC, value ASC
     LIMIT 10)
   SELECT
     top.id        AS lemma_id,
     top.value     AS lemma,
     top.pos       AS pos,
     top.rank      AS rank,
     top.has_exact AS has_exact,
     (SELECT json_group_array(DISTINCT t.value ORDER BY t.rank ASC)
      FROM translations t
      WHERE t.lemma_id = top.id) AS translations,
     top.forms     AS matched_forms
   FROM top
   ORDER BY top.has_exact DESC, top.rank DESC, lemma ASC")


(defn open!
  "Opens the dictionary's SQLite over the worker. Resolves with its handle."
  [deps]
  (sqlite/init! deps))


(defn ready?
  "Whether this tab has the dictionary right now, for a caller that has to say
   so. Nothing on the query path reads it."
  [db]
  (sqlite/ready? db))


(defn- completion
  "One completion map from a result row, with its JSON-array columns read as vectors."
  [{:keys [has_exact lemma matched_forms pos translations]}]
  {:exact?       (pos? has_exact)
   :lemma        lemma
   :matched-forms (js->clj (js/JSON.parse (or matched_forms "[]")))
   :pos          pos
   :translations (js->clj (js/JSON.parse (or translations "[]")))})


(defn ^:async completions
  "Returns a vec of completion maps {:lemma :translations :exact? :pos :matched-forms} for `prefix`; `:matched-forms` are the lemma's normalised forms starting with it.
   An empty vec means no rows or no database; `ready?` tells which."
  [db prefix]
  (let [prefix-start (utils/normalize-german (or prefix ""))]
    (if (empty? prefix-start)
      []
      (let [prefix-end (str prefix-start \z)
            rows       (js->clj
                        (await
                         (sqlite/exec db
                                      #js {:sql         completions-sql
                                           :bind        #js [prefix-start prefix-end prefix-start]
                                           :returnValue "resultRows"
                                           :rowMode     "object"}))
                        :keywordize-keys
                        true)]
        (mapv completion rows)))))
