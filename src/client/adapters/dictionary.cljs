(ns adapters.dictionary
  (:require
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
   Measured in #179: prefix f 95-119 ms -> 20-27 ms.

   Translations travel as a JSON array, not a joined string. GROUP_CONCAT's
   separator is also content: 1100 of the dictionary's translations contain a
   comma, so splitting the join tore `тем, что` into `тем` and ` что` and gave
   `indem` five translations where it has three. A reserved separator would work
   only as long as nobody's rebuild introduces the character, and nothing
   enforces that; an array has no separator to reserve. Measured against the
   shipped dictionary in #362: the aggregate costs +0.025 ms per call, which is
   0.03% of the one-letter prefix, and the #179 shape above is untouched."
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
     (SELECT json_group_array(DISTINCT t.value ORDER BY t.rank ASC)
      FROM translations t
      WHERE t.lemma_id = top.id) AS translations
   FROM top
   ORDER BY top.rank DESC, lemma ASC")


(defn ready?
  [db]
  (sqlite/ready? db))


(defn- completion
  "One completion map from a result row. Translations arrive as the JSON array
   built by completions-sql, so they are read as elements and passed on — a
   translation carrying a comma stays one translation, and a lemma with none
   gets none rather than a blank."
  [{:keys [has_exact lemma pos translations]}]
  {:exact?       (pos? has_exact)
   :lemma        lemma
   :pos          pos
   :translations (js->clj (js/JSON.parse (or translations "[]")))})


(defn ^:async completions
  "Returns a sequence of completion maps {:lemma :translations :exact? :pos} from SQLite."
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
          (map completion rows))))
    []))
