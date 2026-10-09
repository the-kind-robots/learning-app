(ns client.db.sqlite-test
  "The page's half of the dictionary protocol, driven against a stub worker.
   Node has no `Worker`, which is why `db.sqlite/attach` takes one instead of
   making it."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is]]
   [db.sqlite :as sut]))


(defn- stub-worker
  "A message target with the halves a test needs. `posted` collects what the
   page sent, each entry the message and the port it arrived with; `reply!`
   answers one of them on that port, the way the worker does; `deliver!` plays
   a status message back over the worker's own port.

   The ports are real `MessageChannel` ports — Node has those even though it
   has no `Worker` — so a reply travels the path it travels in a browser."
  []
  (let [listeners (atom {})
        posted    (atom [])
        target    #js {:addEventListener
                       (fn [event handler]
                         (swap! listeners update event (fnil conj []) handler))
                       :removeEventListener
                       (fn [event handler]
                         (swap! listeners update event (fn [hs] (vec (remove #{handler} hs)))))
                       :postMessage
                       (fn [msg transfer]
                         (swap! posted conj {:msg msg :port (aget transfer 0)}))}
        reply!    (fn reply!
                    ([message] (reply! 0 message))
                    ([n message]
                     (.postMessage (:port (nth @posted n)) (clj->js message))))]
    {:crash!   (fn []
                 (doseq [handler (get @listeners "error")]
                   (handler #js {})))
     :deliver! (fn [message]
                 (doseq [handler (get @listeners "message")]
                   (handler #js {:data (clj->js message)})))
     :posted   posted
     :reply!   reply!
     :target   target}))


(deftest two-queries-in-flight-get-their-own-answers
  (async-testing "each query is answered on its own port, so the replies cannot be swapped"
    (let [{:keys [posted reply! target]} (stub-worker)
          db    (sut/attach target)
          hund  (sut/exec db #js {:sql "SELECT 1"})
          katze (sut/exec db #js {:sql "SELECT 2"})]
      (is (= 2 (count @posted)))
      ;; Answered out of order on purpose: nothing about the order is what
      ;; pairs a reply with its query.
      (reply! 1 {:result [{:lemma "Katze"}]})
      (reply! 0 {:result [{:lemma "Hund"}]})
      (is (= [{:lemma "Hund"}] (js->clj (await hund) :keywordize-keys true)))
      (is (= [{:lemma "Katze"}] (js->clj (await katze) :keywordize-keys true))))))


(deftest exec-rejects-when-the-worker-reports-an-error
  (async-testing "the failure reaches the caller instead of an empty answer"
    (let [{:keys [reply! target]} (stub-worker)
          db     (sut/attach target)
          answer (sut/exec db #js {:sql "SELECT 1"})]
      (reply! {:error "Missing required OPFS APIs."})
      (try
        (await answer)
        (is false "should have rejected")
        (catch :default err
          (is (= "Missing required OPFS APIs." (ex-message err))))))))


(deftest a-crashed-worker-fails-the-queries-waiting-on-it
  (async-testing "GH-320: a worker error rejects every pending query instead of leaving it hanging"
    (let [{:keys [crash! reply! target]} (stub-worker)
          db       (sut/attach target)
          answered (sut/exec db #js {:sql "SELECT 1"})
          _ (reply! 0 {:result [{:lemma "Hund"}]})
          _ (await answered)
          hund     (sut/exec db #js {:sql "SELECT 2"})
          katze    (sut/exec db #js {:sql "SELECT 3"})]
      (crash!)
      (doseq [pending [hund katze]]
        (try
          (await pending)
          (is false "should have rejected")
          (catch :default err
            (is (re-find #"Dictionary worker failed" (ex-message err))))))
      (let [after (sut/exec db #js {:sql "SELECT 4"})]
        (reply! 3 {:result [{:lemma "Maus"}]})
        (is (= [{:lemma "Maus"}] (js->clj (await after) :keywordize-keys true))
            "a query sent after the crash is not failed by it")))))


