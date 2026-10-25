(ns pages.words.presenter
  (:require
   [clojure.string :as str]
   [collection-accents :as colours]
   [domain.vocabulary :as vocabulary]
   [pages.words.edit :as edit]
   [utils :as utils]))


(defn- retention-band
  "How well the word is remembered, as the list says it: from `level`, a
   percentage."
  [level]
  (cond
    (>= level 80) :retention/excellent
    (>= level 50) :retention/good
    (>= level 20) :retention/due
    :else         :retention/new))


(defn word-item-props
  [{:keys [id value retention-level] :as row} added]
  {:id id
   :added? (contains? added id)
   :retention-band (retention-band retention-level)
   :retention-color (utils/prozent->color retention-level)
   :retention-percent (int retention-level)
   :value value
   :translation (str/join " · " (vocabulary/russian-translations row))})


(defn empty-state
  "Which placeholder replaces the word list, `:empty/no-matches` or `:empty/first-run`, or nil when there are
   rows. `total` counts the words in scope before the filter, which tells an empty vocabulary from no match."
  [words total]
  (when (and (some? total) (empty? words))
    (if (pos? total)
      :empty/no-matches
      :empty/first-run)))


(defn- path-title
  "The name of a collection as a card's title says it: its segments joined by ` / `, and a path of more than
   30 characters and three segments or more cut to its first and last."
  [name]
  (let [segments (str/split (str/trim name) #"\s*/\s*")
        path     (str/join " / " segments)]
    (if (and (< 30 (count path)) (<= 3 (count segments)))
      (str (first segments) " / … / " (last segments))
      path)))


(defn- highlighted-sentence
  "The `sentence` cut around the word it shows, `{:before :hit :after}`: the first place, whatever the case,
   where the `word` stands as written, in the form of a `structure` item of that dictionary form, or without
   its article. `:hit` is empty when the sentence says the word none of these ways."
  [sentence word structure]
  (let [lower      (str/lower-case sentence)
        dictionary (str/lower-case (str word))
        forms      (concat [word]
                           (for [item  structure
                                 :when (= dictionary (str/lower-case (str (:dictionaryForm item))))]
                             (:usedForm item))
                           [(str/replace dictionary #"^(?:der|die|das|ein|eine)\s+" "")])
        [at size]  (some (fn [form]
                           (let [at (if (str/blank? form) -1 (.indexOf lower (str/lower-case form)))]
                             (when-not (neg? at) [at (count form)])))
                         forms)]
    (if at
      {:after  (subs sentence (+ at size))
       :before (subs sentence 0 at)
       :hit    (subs sentence at (+ at size))}
      {:after "" :before sentence :hit ""})))


(defn- changed?
  "Whether the word, its translations or an example of `editing` differs from what the dialog opened with. A
   blank line among the translations is no difference."
  [{:keys [stored-translations stored-word translation-lines word] :as editing}]
  (let [translations (fn [lines] (->> lines (map str/trim) (remove str/blank?) vec))]
    (boolean (or (not= word stored-word)
                 (not= (translations translation-lines) (translations stored-translations))
                 (seq (edit/changed-examples editing))))))


(defn- editing-props
  "What the dialog of `editing` is, in the screen's words: the word and the translations as typed, a card for
   every collection of the word, and the footer's buttons."
  [{:keys [collection-ids collections example-request examples stored-word translation-lines word word-taken?]
    :as   editing}]
  (let [accents  (colours/accents collections)
        names    (zipmap (map :id collections) (map :name collections))
        pending? (= :request/pending (:status example-request))
        status?  (fn [id status]
                   (and (= id (:collection-id example-request)) (= status (:status example-request))))]
    {:examples       (mapv (fn [id]
                             (let [example       (get examples id)
                                   regenerating? (status? id :request/pending)
                                   failed?       (status? id :request/failed)]
                               {:accent        (get accents id)
                                :collection-id id
                                :failed?       failed?
                                :regenerate-disabled? pending?
                                :regenerating? regenerating?
                                :sentence      (highlighted-sentence (:value example) stored-word (:structure example))
                                :status-shown? (or regenerating? failed?)
                                :title         (some-> (get names id) path-title)
                                :translation   (:translation example)}))
                           collection-ids)
     :id             (:id editing)
     :replaces       (when (and (edit/renamed? editing) (not word-taken?)) stored-word)
     :reset-disabled? (not (changed? editing))
     :save-disabled? (not (and (changed? editing) (edit/valid? editing)))
     :stored-word    stored-word
     :translation-rows (max 1 (count translation-lines))
     :translations   translation-lines
     :word           word
     :word-multiline? (:phrase? editing)
     :word-taken?    word-taken?}))


(defn page-props
  "What the word list renders: the rows, the placeholder that replaces them, and whether the page chrome
   applies. An empty vocabulary drops the chrome, an empty filter keeps it; while `total` is nil the list
   shows nothing."
  [{:words/keys [added editing in-collection? more? rows search total]}]
  {:editing        (some-> editing editing-props)
   :in-collection? (boolean in-collection?)
   :empty-state    (empty-state rows total)
   :items          (mapv #(word-item-props % added) rows)
   :known?         (some? total)
   :more?          more?
   :search         search
   :vocabulary?    (boolean (some-> total pos?))})
