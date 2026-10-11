(ns adapters.dictionary
  (:require
   [db.sqlite :as sqlite]
   [utils :as utils]))


(def ^:private completions-sql
  "Top ten lemmas for a prefix range, ranked, each with its translations as a JSON array and the lemma's in-range forms as `forms`.
   Forms come from one grouped range scan, so short prefixes stay cheap."
  "WITH top AS (
     SELECT l.id, l.value, l.pos, l.rank, m.forms
     FROM (SELECT lemma_id, json_group_array(normalized_form) AS forms
           FROM surface_forms
           WHERE normalized_form >= ? AND normalized_form <= ?
           GROUP BY lemma_id) m
     JOIN lemmas l ON l.id = m.lemma_id
     WHERE l.pos NOT IN ('conj', 'particle', 'pron', 'prep')
     ORDER BY l.rank DESC, l.value ASC
     LIMIT 10)
   SELECT
     top.id    AS lemma_id,
     top.value AS lemma,
     top.pos AS pos,
     top.rank AS rank,
     EXISTS (SELECT 1
             FROM surface_forms sf
             WHERE sf.normalized_form = ? AND sf.lemma_id = top.id) AS has_exact,
     (SELECT json_group_array(DISTINCT t.value ORDER BY t.rank ASC)
      FROM translations t
      WHERE t.lemma_id = top.id) AS translations,
     top.forms AS matched_forms
   FROM top
   ORDER BY top.rank DESC, lemma ASC")


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
