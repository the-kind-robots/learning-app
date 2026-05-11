(ns repo.retention
  (:require
   [dbs :as dbs]
   [domain.retention :as domain]
   [utils :as utils]))


(defn ^:async levels
  "Returns retention levels for given word ids."
  ([dbs word-ids] (levels dbs word-ids (utils/now-ms)))
  ([dbs word-ids now-ms]
   (if (seq word-ids)
     (let [{reviews :docs}  (await (dbs/find-all dbs
                                                 {:selector {:type    "review"
                                                             :word-id {:$in (vec word-ids)}}}))
           word-id->reviews (group-by :word-id reviews)]
       (mapv (fn [word-id]
               {:word-id word-id
                :retention-level (domain/retention-level
                                  (word-id->reviews word-id [])
                                  now-ms)})
             word-ids))
     [])))


(defn ^:async level
  "Returns retention level for one word id."
  ([dbs word-id] (level dbs word-id (utils/now-ms)))
  ([dbs word-id now-ms]
   (let [{reviews :docs} (await (dbs/find-all dbs
                                              {:selector {:type    "review"
                                                          :word-id word-id}}))]
     (domain/retention-level reviews now-ms))))
