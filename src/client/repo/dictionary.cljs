(ns repo.dictionary
  (:require
   [clojure.string :as str]
   [dbs :as dbs]
   [utils :as utils]))


(def ^:private completions-sql
  "SELECT
     l.id    AS lemma_id,
     l.value AS lemma,
     l.pos AS pos,
     l.rank AS rank,
     MAX(sf.normalized_form = ?) AS has_exact,
     GROUP_CONCAT(DISTINCT t.value ORDER BY t.rank ASC) AS translations
   FROM surface_forms sf
   JOIN lemmas l ON l.id = sf.lemma_id
   LEFT JOIN translations t ON t.lemma_id = l.id
   WHERE sf.normalized_form >= ? AND sf.normalized_form <= ?
     AND l.pos NOT IN ('conj', 'particle', 'pron', 'prep')
   GROUP BY l.id
   ORDER BY l.rank DESC, lemma ASC
   LIMIT 10")


(defn ready? [] (dbs/dictionary-ready?))


(defn ^:async completions
  "Returns a vec of completion maps {:lemma :translation :exact?} from SQLite."
  [dbs prefix]
  (when-let [db (:dictionary/db dbs)]
    (let [prefix-start (utils/normalize-german (or prefix ""))]
      (if (empty? prefix-start)
        []
        (let [;;appending \z it makes the upper bound
              ;; cover every possible suffix, turning the range into a prefix scan
              prefix-end (str prefix-start \z)
              rows       (js->clj
                          (await
                           (.exec db
                                  #js {:sql         completions-sql
                                       :bind        #js [prefix-start prefix-start prefix-end]
                                       :returnValue "resultRows"
                                       :rowMode     "object"}))
                          :keywordize-keys
                          true)]
          (for [{:keys [lemma translations has_exact]} rows]
            {:lemma        lemma
             :translations (str/split (or translations "") #",")
             :exact?       (pos? has_exact)}))))))
