(ns adapters.dictionary
  (:require
   [clojure.string :as str]
   [db.sqlite :as sqlite]
   [utils :as utils]))


(def ^:private completions-sql
  "WITH top AS (
     SELECT l.id, l.value, l.pos, l.rank
     FROM (SELECT DISTINCT lemma_id
           FROM surface_forms
           WHERE normalized_form >= ? AND normalized_form <= ?) m
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
     (SELECT GROUP_CONCAT(DISTINCT t.value ORDER BY t.rank ASC)
      FROM translations t
      WHERE t.lemma_id = top.id) AS translations
   FROM top
   ORDER BY top.rank DESC, lemma ASC")


(defn ready?
  [db]
  (sqlite/ready? db))


(defn ^:async completions
  "Returns a vec of completion maps {:lemma :translation :exact?} from SQLite."
  [db prefix]
  (if (ready? db)
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
          (for [{:keys [lemma translations has_exact]} rows]
            {:lemma        lemma
             :translations (str/split (or translations "") #",")
             :exact?       (pos? has_exact)}))))
    []))
