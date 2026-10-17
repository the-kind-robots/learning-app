(ns use-cases.examples
  "The rules of fetching the examples memory is missing: which pair needs
   one, what a request asks the backend about, and which failures mean the
   word will not get one. `application.example-backfill` runs the fetching; the
   behaviour is in `specs/example-backfill/spec.md`."
  (:require
   [domain.examples :as examples]
   [utils :as utils]))


(defn subject
  "What a request for `pair` asks the backend about."
  [{:keys [collection-name word]}]
  (cond-> {:collection-name collection-name
           :translations (into []
                               (comp (filter #(= "ru" (:lang %)))
                                     (map :value)
                                     (filter utils/non-blank))
                               (:translation word))
           :word (:value word)}
    (:pos word) (assoc :pos (:pos word))))


(def failures-without-example
  "The failures that mean the entry will not get an example."
  #{:failure/invalid-subject :failure/invalid-response :failure/rejected})


(defn next-pair
  "The first pair of `entries`, a chunk of the words in `memory`, that has
   no example and whose subject is not in `failed-subjects`, or nil."
  [{:learner/keys [collections-of examples-of]} memory failed-subjects entries]
  (some (fn [entry]
          (->> (examples/missing-pairs entry
                                       (collections-of memory (:id entry))
                                       (examples-of memory [(:id entry)]))
               (remove (comp failed-subjects subject))
               first))
        entries))


(defn save-example!
  "Saves the fetched `example` of `pair`. Resolves true when the learner's
   data holds it afterwards."
  [{:learner/keys [insert!]} pair example]
  (insert! :entity/example
           {:collection-id (:collection-id pair)
            :example       example
            :word          (:value (:word pair))
            :word-id       (:id (:word pair))}))
