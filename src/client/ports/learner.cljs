(ns ports.learner
  "The learner's data, as the use cases reach it: memory to read, every
   write, and the active collection. `adapters.learner` does the work."
  (:require
   [adapters.learner :as learner]
   [adapters.learner.memory :as memory]))


(def reads
  "The reads of memory, each a function of a memory value: what the use
   cases and pages ask of the learner's data. The port hands them out, so
   nothing above the adapters requires the memory namespace."
  {:learner/collection       memory/collection
   :learner/collection-cards memory/collection-cards
   :learner/collection-words memory/collection-words
   :learner/collections      memory/collections
   :learner/examples-of      memory/examples-of
   :learner/review-history   memory/review-history
   :learner/word             memory/word
   :learner/word-count       memory/word-count
   :learner/words            memory/words})


(defn start!
  [{:keys [clock db store]}]
  (let [learner {:clock clock
                 :dbs   db
                 :store store}
        ;; One move per port, started by whoever asks first.
        moved   (delay (learner/examples-moved! learner))]
    (merge
     reads
     {:learner/memory (fn memory
                        []
                        (learner/current-memory learner))
      :learner/active-collection (fn active-collection
                                   []
                                   (learner/active-collection learner))
      :learner/set-active-collection! (fn set-active-collection!
                                        [collection-id]
                                        (learner/set-active-collection! learner collection-id))
      :learner/loaded (fn loaded
                        []
                        (learner/loaded learner))
      :learner/catch-up! (fn catch-up!
                           []
                           (learner/catch-up! learner))
      :learner/add-word! (fn add-word!
                           [entry]
                           (learner/add-word! learner entry))
      :learner/update-word! (fn update-word!
                              [word-id change]
                              (learner/update-word! learner word-id change))
      :learner/delete-word! (fn delete-word!
                              [word-id]
                              (learner/delete-word! learner word-id))
      :learner/add-review! (fn add-review!
                             [word-id retained translation]
                             (learner/add-review! learner word-id retained translation))
      :learner/create-collection! (fn create-collection!
                                    [name]
                                    (learner/create-collection! learner name))
      :learner/rename-collection! (fn rename-collection!
                                    [collection-id new-name]
                                    (learner/rename-collection! learner collection-id new-name))
      :learner/delete-collection! (fn delete-collection!
                                    [collection-id]
                                    (learner/delete-collection! learner collection-id))
      :learner/add-to-collection! (fn add-to-collection!
                                    [word-id collection-id]
                                    (learner/add-to-collection! learner word-id collection-id))
      :learner/remove-from-collection! (fn remove-from-collection!
                                         [word-id collection-id]
                                         (learner/remove-from-collection! learner word-id collection-id))
      :learner/examples-moved (fn examples-moved
                                []
                                @moved)
      :learner/hold-fetches-until! (fn hold-fetches-until!
                                     [ready]
                                     (learner/hold-fetches-until! learner ready))
      :learner/cancel-answered-fetches! (fn cancel-answered-fetches!
                                          [example-ids]
                                          (learner/cancel-answered-fetches! learner example-ids))
      :learner/request-examples! (fn request-examples!
                                   [requests]
                                   (learner/request-examples! learner requests))})))
