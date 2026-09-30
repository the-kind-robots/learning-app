(ns ports.collections
  (:require
   [adapters.active-collection :as active-collection]
   [adapters.collections :as collections]))


(defn start!
  [{:keys [clock db store]}]
  {:collections/active-id     (fn active-id
                                []
                                ;; The active collection is the one memory
                                ;; holds under the stored id. An id memory
                                ;; holds no collection under — deleted here or
                                ;; on another device — is none: «Всё подряд».
                                (let [id (active-collection/active-collection-id)]
                                  (when (some-> store deref (get-in [:learner/memory :collections id]))
                                    id)))
   :collections/set-active!   active-collection/set-active-collection!
   :collections/list          (fn list [] (collections/list-collections db))
   :collections/get           (fn get
                                [collection-id]
                                (collections/get-collection db collection-id))
   :collections/create!       (fn create!
                                [name]
                                (collections/create-collection! db clock name))
   :collections/rename!       (fn rename!
                                [collection-id new-name]
                                (collections/rename-collection! db collection-id new-name))
   :collections/delete!       (fn delete!
                                [collection-id]
                                (collections/delete-collection! db collection-id))
   :collections/docs-without-word (fn docs-without-word
                                    [word-id]
                                    (collections/docs-without-word db word-id))
   :collections/add-word!     (fn add-word!
                                [word-id collection-id]
                                (collections/add-word-to-collection! db word-id collection-id))
   :collections/exclude-word! (fn exclude-word!
                                [word-id collection-id]
                                (collections/exclude-word! db word-id collection-id))})
