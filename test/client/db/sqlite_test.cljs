(ns client.db.sqlite-test
  "The page's half of the dictionary protocol, driven against a stub worker.
   Node has no `Worker`, which is why `db.sqlite/attach` takes one instead of
   making it."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.dictionary :as dictionary]
   [cljs.test :refer-macros [deftest is testing]]
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


(defn- pending-work
  "A promise that settles after everything already queued. `completions` is an
   async function, so its first statement runs a microtask deep and the query
   it sends has not left yet when the caller gets the promise back."
  []
  (js/Promise. (fn [resolve] (js/setTimeout resolve 0))))


(deftest holding-state-reads-one-message
  (testing "what a message says about this tab holding the database, and nothing else"
    (is (true? (sut/holding-state #js {:type "ready"})))
    (is (false? (sut/holding-state #js {:type "loading"})))
    (is (false? (sut/holding-state #js {:message "Missing required OPFS APIs." :type "error"})))
    (is (nil? (sut/holding-state #js {:durationMs 12 :phase "db-open" :status "ok" :type "phase"}))
        "a phase report is not about ownership")
    (is (nil? (sut/holding-state #js {:result []}))
        "and neither is anything without a type")))


(deftest status-messages-do-not-disturb-a-query
  (async-testing "loading, phases, ready, error and a crash all pass through, and the answer still lands"
    (let [{:keys [crash! deliver! reply! target]} (stub-worker)
          db     (sut/attach target)
          answer (sut/exec db #js {:sql "SELECT 1"})]
      ;; Status arrives on the worker's own port and the reply on the
      ;; query's, so these cannot be mistaken for it — which is the point of
      ;; giving each query a channel.
      (deliver! {:type "loading"})
      (deliver! {:durationMs 12 :phase "db-open" :status "ok" :type "phase"})
      (deliver! {:type "ready"})
      (deliver! {:message "Missing required OPFS APIs." :type "error"})
      (crash!)
      (reply! {:result [{:lemma "Hund"}]})
      (is (= [{:lemma "Hund"}] (js->clj (await answer) :keywordize-keys true))))))


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


(deftest completions-are-queried-with-no-readiness-message
  (async-testing "GH-351: the query goes out before anything says ready, and its answer is used"
    ;; Nothing has said "ready" and nothing here would have gated on it: the
    ;; worker answers with whatever it has, so the query is always sent.
    (let [{:keys [posted reply! target]} (stub-worker)
          db     (sut/attach target)
          answer (dictionary/completions db "Hund")]
      (await (pending-work))
      (is (= 1 (count @posted))
          "no readiness gate swallowed the query")
      (reply! {:result [{:has_exact 1 :lemma "Hund" :pos "noun" :translations "[\"собака\",\"пёс\"]"}]})
      (is (= [{:exact?       true
               :lemma        "Hund"
               :pos          "noun"
               :translations ["собака" "пёс"]}]
             (await answer))))))


(deftest a-tab-without-the-dictionary-answers-no-completions
  (async-testing "GH-351: no rows is an ordinary answer, not a rejection and not a wait"
    (let [{:keys [reply! target]} (stub-worker)
          db     (sut/attach target)
          answer (dictionary/completions db "Hund")]
      (await (pending-work))
      (reply! {:result []})
      (is (= [] (await answer))))))


(deftest ready-reports-whether-this-tab-has-the-dictionary
  (async-testing "GH-351: a turn taken and given back, read through the port's predicate"
    (let [{:keys [crash! deliver! target]} (stub-worker)
          db (sut/attach target)]
      (is (false? (dictionary/ready? db)) "nothing has said ready yet")
      (deliver! {:type "ready"})
      (is (true? (dictionary/ready? db)) "this tab took its turn")
      (deliver! {:type "loading"})
      (is (false? (dictionary/ready? db)) "and gave it back")
      (deliver! {:type "ready"})
      (deliver! {:message "Missing required OPFS APIs." :type "error"})
      (is (false? (dictionary/ready? db)) "a context that could not open it has nothing")
      (deliver! {:type "ready"})
      (crash!)
      (is (false? (dictionary/ready? db)) "and neither has a crashed worker"))))


(deftest an-empty-prefix-asks-the-worker-nothing
  (async-testing "an emptied input answers [] without a round trip"
    (let [{:keys [posted target]} (stub-worker)
          db (sut/attach target)]
      (is (= [] (await (dictionary/completions db ""))))
      (is (= [] @posted)))))
