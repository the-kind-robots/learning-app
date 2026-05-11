(ns repo.dictionary
  (:require
   [dbs :as dbs]
   [utils :as utils]))


(def ^:private suggest-sql
  "SELECT
     l.id    AS lemma_id,
     l.value AS lemma,
     l.pos,
     l.rank,
     t.value AS translation,
     MAX(CASE WHEN sf.normalized_form = ? THEN 1 ELSE 0 END) AS has_exact
   FROM surface_forms sf
   JOIN lemmas l ON l.id = sf.lemma_id
   LEFT JOIN translations t ON t.lemma_id = l.id AND t.rank = 1
   WHERE sf.normalized_form >= ? AND sf.normalized_form < ?
   GROUP BY l.id
   ORDER BY has_exact DESC, l.rank ASC
   LIMIT 10")


(defn ready? [] (dbs/dictionary-ready?))


(defn ^:async suggest
  "Returns {:suggestions [...] :prefill string-or-nil} from SQLite.
   Falls back to empty when db is not yet ready."
  [dbs input]
  (let [db (:dictionary/db dbs)]
    (if-not db
      {:suggestions [] :prefill nil}
      (let [normalized (utils/normalize-german (or input ""))]
        (if (empty? normalized)
          {:suggestions [] :prefill nil}
          (let [prefix-end (str normalized "￿")
                rows       (js->clj (await (.exec db
                                                  #js {:sql         suggest-sql
                                                       :bind        #js [normalized normalized prefix-end]
                                                       :returnValue "resultRows"
                                                       :rowMode     "object"}))
                                    :keywordize-keys
                                    true)
                prefill    (->> rows (filter #(pos? (:has_exact %))) first :lemma)]
            {:suggestions (mapv (fn [{:keys [lemma_id lemma pos rank translation]}]
                                  {:lemma-id    lemma_id
                                   :lemma       lemma
                                   :pos         pos
                                   :rank        rank
                                   :translation translation})
                                rows)
             :prefill     prefill}))))))
