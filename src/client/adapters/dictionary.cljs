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
   0.03% of the one-letter prefix, and the #179 shape above is untouched.

   Each row also carries the forms of its lemma that fell in the range —
   `ging`, `ginge` for gehen on `ging` — as a JSON array, so the kept list can
   tell on the next keystroke whether the row still completes the field
   (#535). They are gathered in the one range scan: GROUP BY lemma_id in
   place of DISTINCT, with the same lemma set and the same ten winners. A
   per-row subquery over the range would scan it ten times more; measured on
   the shipped dictionary (JDBC, median of 100 warm runs): s 34 ms -> 29-30
   with the group and 70 with the subquery, a 25 -> 20 and 52-57, hau 1.1 ->
   1.4-1.5 and 2.3."
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
  "One completion map from a result row. Translations arrive as the JSON array
   built by completions-sql, so they are read as elements and passed on — a
   translation carrying a comma stays one translation, and a lemma with none
   gets none rather than a blank."
  [{:keys [has_exact lemma matched_forms pos translations]}]
  {:exact?       (pos? has_exact)
   :lemma        lemma
   :matched-forms (js->clj (js/JSON.parse (or matched_forms "[]")))
   :pos          pos
   :translations (js->clj (js/JSON.parse (or translations "[]")))})


(defn ^:async completions
  "Returns a vec of completion maps {:lemma :translations :exact? :pos
   :matched-forms} from SQLite; `:matched-forms` are the lemma's normalised
   forms that start with the prefix.

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
        (mapv completion rows)))))
