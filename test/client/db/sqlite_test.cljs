(ns client.db.sqlite-test
  "The page's half of the dictionary protocol, driven against a stub worker.
   Node has no `Worker`, which is why `db.sqlite/attach` takes one instead of
   making it."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.dictionary :as dictionary]
   [cljs.test :refer-macros [deftest is]]
   [db.sqlite :as sut]))


(defn- stub-worker
  "A message target with the two halves a test needs: `posted` collects what
   the page sent, `deliver!` plays a message back from the worker."
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
                       (fn [msg]
                         (swap! posted conj msg))}]
    {:crash!   (fn []
                 (doseq [handler (get @listeners "error")]
                   (handler #js {})))
     :deliver! (fn [message]
                 (doseq [handler (get @listeners "message")]
                   (handler #js {:data (clj->js message)})))
     :posted   posted
     :target   target}))


(defn- pending-work
  "A promise that settles after everything already queued. `completions` is an
   async function, so its first statement runs a microtask deep and the query
   it sends has not left yet when the caller gets the promise back."
  []
  (js/Promise. (fn [resolve] (js/setTimeout resolve 0))))


(deftest lifecycle-messages-leave-the-request-protocol-alone
  (async-testing "loading, phases, ready, error and a crash all pass through, and the answer still lands"
    (let [{:keys [crash! deliver! target]} (stub-worker)
          db     (sut/attach target)
          answer (sut/exec db #js {:sql "SELECT 1"})]
      ;; These carry no `id`, so the response matcher must ignore them rather
      ;; than read `undefined` and resolve the wrong promise.
      (deliver! {:type "loading"})
      (deliver! {:durationMs 12 :phase "db-open" :status "ok" :type "phase"})
      (deliver! {:type "ready"})
      (deliver! {:message "Missing required OPFS APIs." :type "error"})
      (crash!)
      (deliver! {:id 1 :result [{:lemma "Hund"}]})
      (is (= [{:lemma "Hund"}] (js->clj (await answer) :keywordize-keys true))))))


(deftest exec-resolves-the-response-with-the-matching-id
  (async-testing "an answer for another request is ignored, the matching one resolves"
    (let [{:keys [deliver! posted target]} (stub-worker)
          db     (sut/attach target)
          answer (sut/exec db #js {:sql "SELECT 1"})]
      (is (= 1 (count @posted)))
      (is (= 1 (.-id (first @posted))))
      (deliver! {:id 99 :result [{:wrong true}]})
      (deliver! {:id 1 :result [{:lemma "Hund"}]})
      (is (= [{:lemma "Hund"}] (js->clj (await answer) :keywordize-keys true))))))


(deftest exec-rejects-when-the-worker-reports-an-error
  (async-testing "the failure reaches the caller instead of an empty answer"
    (let [{:keys [deliver! target]} (stub-worker)
          db     (sut/attach target)
          answer (sut/exec db #js {:sql "SELECT 1"})]
      (deliver! {:error "Missing required OPFS APIs." :id 1})
      (try
        (await answer)
        (is false "should have rejected")
        (catch :default err
          (is (= "Missing required OPFS APIs." (ex-message err))))))))


(deftest completions-are-queried-before-the-dictionary-is-ready
  (async-testing "GH-351: the query goes out with no readiness message received, and its answer is used"
    ;; Nothing has said "ready" and nothing here would have listened if it
    ;; had: the query is sent regardless and the worker holds it.
    (let [{:keys [deliver! posted target]} (stub-worker)
          db     (sut/attach target)
          answer (dictionary/completions db "Hund")]
      (await (pending-work))
      (is (= 1 (count @posted))
          "no readiness gate swallowed the query")
      (deliver! {:id     1
                 :result [{:has_exact 1 :lemma "Hund" :pos "noun" :translations "собака,пёс"}]})
      (is (= [{:exact?       true
               :lemma        "Hund"
               :pos          "noun"
               :translations ["собака" "пёс"]}]
             (await answer))))))


(deftest an-empty-prefix-asks-the-worker-nothing
  (async-testing "an emptied input answers [] without a round trip"
    (let [{:keys [posted target]} (stub-worker)
          db (sut/attach target)]
      (is (= [] (await (dictionary/completions db ""))))
      (is (= [] @posted)))))
