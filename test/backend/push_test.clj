(ns backend.push-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [push :as push]))


(deftest pokes-go-to-subscribed-accounts-only
  (testing "one answer of the feed → pokes for updated userdbs, dedup within the turn"
    (let [poked (atom [])]
      (with-redefs [push/send-poke! (fn [ch] (swap! poked conj ch))]
        (push/subscribe! 3 :channel-a)
        (push/subscribe! 3 :channel-b)
        (push/subscribe! 9 :channel-c)
        (try
          (let [seq-after (#'push/poke-updated-dbs!
                           {:results  [{:db_name "userdb-3" :type "updated"}
                                       {:db_name "userdb-3" :type "updated"}
                                       {:db_name "dictionary-db" :type "updated"}
                                       {:db_name "_global_changes" :type "updated"}
                                       {:db_name "userdb-5" :type "updated"}
                                       {:db_name "userdb-9" :type "deleted"}]
                            :last_seq "42-abc"})]
            (is (= "42-abc" seq-after))
            (is
             (= #{:channel-a :channel-b} (set @poked))
             "account 3 twice-updated pokes once per channel; dictionary-db and _global_changes have no possible subscribers; 5 has no channel; 9 was deleted, not updated"))
          (finally
           (push/unsubscribe! 3 :channel-a)
           (push/unsubscribe! 3 :channel-b)
           (push/unsubscribe! 9 :channel-c)))))))


(deftest unsubscribing-the-last-channel-clears-the-account
  (push/subscribe! 4 :only)
  (push/unsubscribe! 4 :only)
  (is (nil? (get @@#'push/channels "userdb-4"))))
