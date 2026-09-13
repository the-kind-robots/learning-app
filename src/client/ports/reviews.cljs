(ns ports.reviews
  (:require
   [adapters.reviews :as reviews]))


(defn start!
  [{:keys [clock db]}]
  {:reviews/by-word       (fn by-word
                            [word-ids]
                            (reviews/reviews-by-word db word-ids))
   :reviews/save!         (fn save!
                            [word-id retained translation]
                            (reviews/save-review! db clock word-id retained translation))
   :reviews/tombstones-of (fn tombstones-of
                            [word-id]
                            (reviews/tombstones-of db word-id))})
