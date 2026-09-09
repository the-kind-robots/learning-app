(ns adapters.dictionary
  (:require
   [clojure.string :as str]
   [db.sqlite :as sqlite]
   [utils :as utils]))


(def ^:private completions-sql
  "Top ten lemmas for a prefix range, cheap on short prefixes too.

   Shape matters here. The inner SELECT DISTINCT collapses surface forms to
   lemma ids before anything else: one lemma owns many in-range forms (Fenster,
   Fensters, Fenstern...), and LIMIT counts rows, so without the collapse ten
   rows would mean ten forms of maybe three lemmas. Rank then picks the winners
   while the query still carries only (id, value, pos, rank) — no translations
   joined, nothing concatenated. has_exact and translations are point lookups
   for the surviving ten alone. The previous shape joined and GROUP_CONCATed
   every in-range lemma — thousands on a one-letter prefix — and discarded all
   but ten after sorting, which is why short prefixes cost ~100x more.
   Measured in #179: prefix f 95-119 ms -> 20-27 ms."
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
  "Whether this tab has the dictionary right now, for a caller that has to say
   so. Nothing on the query path reads it."
  [db]
  (sqlite/ready? db))


(defn ^:async completions
  "Returns a vec of completion maps {:lemma :translations :exact? :pos} from SQLite.

   No readiness gate in front of the query: a tab without the database answers
   with no rows on its own, so a gate here would only duplicate the decision
   (#351). An empty vec therefore means either — `ready?` is what separates
   them. The caller drops answers the user has typed past."
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
        (mapv (fn [{:keys [lemma translations has_exact pos]}]
                {:exact?       (pos? has_exact)
                 :lemma        lemma
                 :pos          pos
                 :translations (str/split (or translations "") #",")})
              rows)))))
