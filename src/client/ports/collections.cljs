(ns ports.collections
  (:require
   [adapters.active-collection :as active-collection]
   [adapters.collections :as collections]))


(defn start!
  [{:keys [clock db]}]
  {:collections/active-id     active-collection/active-collection-id
   :collections/set-active!   active-collection/set-active-collection!
   :collections/list          (fn list [] (collections/list-collections db))
   :collections/get           (fn get
                                [collection-id]
                                (collections/get-collection db collection-id))
   :collections/create!       (fn create!
                                [name]
                                (collections/create-collection! db clock name))
   :collections/rename!       (fn rename!
                                [collection-doc new-name]
                                (collections/rename-collection! db collection-doc new-name))
   :collections/delete!       (fn delete!
                                [collection-id]
                                (collections/delete-collection! db collection-id))
   :collections/add-word!     (fn add-word!
                                [word-id collection-id]
                                (collections/add-word-to-collection! db word-id collection-id))
   :collections/exclude-word! (fn exclude-word!
                                [word-id collection-id]
                                (collections/exclude-word! db word-id collection-id))})
