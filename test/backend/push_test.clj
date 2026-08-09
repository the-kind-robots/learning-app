(ns backend.push-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [db :as db]
   [push :as push]))


(deftest pokes-go-to-subscribed-accounts-only
  (testing "one answer of the feed → pokes for updated userdbs, dedup within the turn"
    (let [poked (atom [])]
      (with-redefs [push/send-poke! (fn [ch] (swap! poked conj ch))]
        (push/subscribe! 3 :channel-a)
        (push/subscribe! 3 :channel-b)
        (push/subscribe! 9 :channel-c)
        (try
          (let [seq-after (#'push/poke-subscribers!
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


(deftest unsubscribing-the-last-channel-stops-its-pokes
  (push/subscribe! 4 :only)
  (push/unsubscribe! 4 :only)
  (is (empty? (get @@#'push/channels "userdb-4"))))


(deftest failed-turn-backs-off-by-error-type
  (testing "refused credentials back off for minutes and log loudly"
    (doseq [status [401 403]]
      (let [{:keys [level sleep-ms]} (#'push/backoff (ex-info "Could not read _db_updates" {:status status}))]
        (is (= :error level))
        (is
         (<= 60000 sleep-ms)
         "minutes, not seconds — a steady retry of bad credentials trips the server's lockout"))))

  (testing "everything else keeps the quick retry"
    (doseq [error [(java.net.ConnectException. "Connection refused")
                   (ex-info "Could not read _db_updates" {:status 404 :body {:error "not_found"}})
                   (ex-info "no status at all" {})]]
      (let [{:keys [level sleep-ms]} (#'push/backoff error)]
        (is (= :warn level))
        (is (= 5000 sleep-ms))))))


(deftest refused-feed-turn-is-auth-shaped-end-to-end
  (testing "db-updates carries the answer's status in ex-data, so the fan-in tells a refusal from an outage"
    (with-redefs [db/request-sync (fn [_conn _opts] {:status 401 :body {:error "unauthorized"}})]
      (let [error (try
                    (db/db-updates "now" 1)
                    (catch Exception e e))]
        (is (= 401 (:status (ex-data error))))
        (is (= :error (:level (#'push/backoff error))))))))
