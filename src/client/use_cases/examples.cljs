(ns use-cases.examples
  "The rules of fetching the examples memory is missing: which pair needs one, what a request asks, and which
   failures mean the word gets none. `application.example-backfill` runs it (`specs/example-backfill/spec.md`)."
  (:require
   [domain.examples :as examples]
   [ports.learner :as learner]
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
  "The first pair of `entries`, a chunk of the learner's words, that has
   no example and whose subject is not in `failed-subjects`, or nil."
  [learner failed-subjects entries]
  (some (fn [entry]
          (->> (examples/missing-pairs entry
                                       (learner/collections-of learner (:id entry))
                                       (learner/examples-of learner [(:id entry)]))
               (remove (comp failed-subjects subject))
               first))
        entries))


(defn save-example!
  "Saves the fetched `example` of `pair`. Resolves with the example's entity, or nil when the learner's data
   does not hold it afterwards."
  [learner pair example]
  (learner/insert! learner
                   :entity/example
                   {:collection-id (:collection-id pair)
                    :example       example
                    :word          (:value (:word pair))
                    :word-id       (:id (:word pair))}))


(defn ^:async replace!
  "Stores the example of `replacement`, `{:collection-id :word-id :word :example}`, and deletes the other
   examples of its collection and word. Resolves true when only the new one is left, false when it was not
   stored or the delete was taken back."
  [learner {:keys [collection-id word-id] :as replacement}]
  (if-let [stored (await (learner/insert! learner :entity/example replacement))]
    (let [replaced (->> (learner/examples-of learner [word-id])
                        (filter #(= collection-id (:collection-id %)))
                        (map :id)
                        (remove #{(:id stored)}))]
      (or (empty? replaced)
          (not (false? (await (learner/change! learner
                                               (mapv (fn [id] [:entity/example id (constantly :entity/deleted)])
                                                     replaced)))))))
    false))
