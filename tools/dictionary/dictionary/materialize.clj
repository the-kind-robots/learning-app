(ns dictionary.materialize
  (:require [clojure.string :as str]
            [utils :refer [normalize-german]]))


(def ^:private label-aliases
  {"bookish" "elevated"
   "legal"   "law"
   "medical" "medicine"})


(defn normalize-label
  [name]
  (get label-aliases name name))


(defn- resolve-label
  [abbr]
  (when (string? abbr)
    (case (-> abbr str/trim str/lower-case)
      ("разг.")                  "colloquial"
      ("вульг.")                 "vulgar"
      ("груб.")                  "rude"
      ("жарг.")                  "slang"
      ("пренебр." "неодобр.")    "pejorative"
      ("бран.")                  "expressive"
      ("ирон." "шутл.")          "ironic"
      ("высок." "книжн.")        "elevated"
      ("офиц.")                  "formal"
      ("поэт.")                  "poetic"
      ("перен.")                 "figurative"
      ("спец.")                  "specialized"
      ("редк.")                  "rare"
      ("уст." "устар.")          "archaic"
      ("тех.")                   "technical"
      ("австр.")                 "austrian"
      ("швейц.")                 "swiss"
      ("сев.-нем." "северонем." "северогерм.") "northern-german"
      ("юж.-нем." "южн." "ю.-нем.") "southern-german"
      ("бавар." "бав.")          "bavarian"
      ("шваб.")                  "swabian"
      ("диал.")                  "dialectal"
      ("рег." "регион.")         "regional"
      ("юр." "юрид.")            "law"
      ("мед.")                   "medicine"
      ("ист." "истор.")          "historical"
      ("рел." "религ.")          "religion"
      ("лингв.")                 "linguistics"
      ("инф." "информ." "комп.") "computing"
      ("воен.")                  "military"
      ("бот.")                   "botany"
      ("зоол.")                  "zoology"
      ("биол.")                  "biology"
      ("хим.")                   "chemistry"
      ("физ.")                   "physics"
      ("мат." "геом.")           "mathematics"
      ("геогр.")                 "geography"
      ("геол.")                  "geology"
      ("муз.")                   "music"
      ("спорт.")                 "sports"
      ("архит.")                 "architecture"
      ("фин." "экон.")           "finance"
      ("филос.")                 "philosophy"
      ("мор.")                   "nautical"
      ("кулин.")                 "culinary"
      ("шахм.")                  "chess"
      ("церк.")                  "church"
      ("анат.")                  "anatomy"
      ("полит.")                 "political"
      ("психол." "псих.")        "psychology"
      ("горн.")                  "mining"
      ("лит.")                   "literature"
      ("иск.")                   "art"
      ("миф.")                   "mythology"
      ("строит.")                "construction"
      ("астрон.")                "astronomy"
      ("авто." "авто")           "automotive"
      ("с.-х.")                  "agricultural"
      ("театр.")                 "theater"
      ("фото." "фото")           "photography"
      ("охот.")                  "hunting"
      ("физиол.")                "physiology"
      ("библ.")                  "biblical"
      ("авиац.")                 "aviation"
      ("метеор.")                "meteorology"
      ("ж.-д.")                  "railway"
      ("вет.")                   "veterinary"
      ("геральд.")               "heraldry"
      ("этн.")                   "ethnographic"
      ("карт.")                  "card-games"
      ("фон.")                   "phonetics"
      ("полигр.")                "printing"
      ("археол.")                "archaeology"
      nil)))


(defn strip-label-parens
  "Strip trailing '(abbr., abbr.)' from value; extracts known labels, reattaches unknown parts.
   Returns [stripped-value label-seq] or nil if no labels found."
  [value]
  (when-let [[_ base label-part] (re-find #"(.*\S)\s*\(([^)]*)\)$" value)]
    (let [parts    (map str/trim (str/split label-part #","))
          resolved (distinct (keep resolve-label parts))
          leftover (remove resolve-label parts)]
      (when (seq resolved)
        [(if (seq leftover)
           (format "%s (%s)" base (str/join ", " leftover))
           base)
         resolved]))))


(defn- surface-forms
  [lemma-id value forms]
  (let [[_ word] (re-matches #"(?:der|die|das)\s+(.*)" value)
        bare     (or word value)
        norms    (distinct (map normalize-german (concat [value bare] forms)))]
    (for [norm  norms
          :when (seq norm)]
      {:normalized-form norm
       :lemma-id        lemma-id})))


(defn normalize-translation
  [{:keys [lemma-id rank value labels]}]
  (let [stored-labels                 (map normalize-label labels)
        [final-value stripped-labels] (or (strip-label-parens value) [value []])
        all-labels                    (distinct (concat stored-labels stripped-labels))]
    {:lemma-id lemma-id
     :rank     rank
     :value    final-value
     :labels   (when (pos? (count all-labels)) all-labels)}))


(defn dedupe-translations
  "Remove duplicate translation values within each lemma; re-rank to fill gaps."
  [translations]
  (->> translations
       (group-by :lemma-id)
       (mapcat (fn [[_ ts]]
                 (->> ts
                      (sort-by :rank)
                      (reduce (fn [{:keys [seen result]} t]
                                (if (seen (:value t))
                                  {:seen seen :result result}
                                  {:seen   (conj seen (:value t))
                                   :result (conj result t)}))
                              {:seen #{} :result []})
                      :result
                      (map-indexed (fn [i t] (assoc t :rank (inc i)))))))))


(defn build-table-data
  [lemmas]
  (let [indexed-lemmas    (->> lemmas
                               (map-indexed (fn [idx lemma]
                                              [(inc idx) lemma])))
        lemma-rows        (->> indexed-lemmas
                               (map (fn [[id lemma]]
                                      (-> (assoc lemma :id id)
                                          (select-keys [:id :value :pos :rank])))))
        translations      (->> indexed-lemmas
                               (mapcat (fn [[lemma-id lemma]]
                                         (map-indexed (fn [idx translation]
                                                        {:lemma-id lemma-id
                                                         :rank     (inc idx)
                                                         :value    (:value translation)
                                                         :labels   (-> translation :meta :labels)})
                                                      (:translations lemma))))
                               (map normalize-translation)
                               (dedupe-translations))
        translation-rows  (->> translations
                               (map #(select-keys % [:lemma-id :rank :value])))
        all-labels        (->> translations (mapcat :labels) distinct sort)
        label-rows        (->> all-labels
                               (map-indexed (fn [idx label]
                                              {:id   (inc idx)
                                               :name label})))
        label->id         (->> label-rows
                               (map (juxt :name :id))
                               (into {}))
        translation-label-rows (->> translations
                                    (filter :labels)
                                    (mapcat (fn [{:keys [lemma-id rank labels]}]
                                              (for [label labels]
                                                {:lemma-id lemma-id
                                                 :rank     rank
                                                 :label-id (label->id label)}))))
        surface-form-rows (->> indexed-lemmas
                               (mapcat (fn [[lemma-id {:keys [value forms]}]]
                                         (surface-forms lemma-id value forms))))]
    {:lemmas        lemma-rows
     :translations  translation-rows
     :labels        label-rows
     :translation-labels translation-label-rows
     :surface-forms surface-form-rows}))
