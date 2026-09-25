(ns client.sync-pull-test
  "When passes run: one at a time, one follow-up for whatever asked during a
   pass, and nothing for the pull's own writes (#319). Replication is a fake
   the test finishes by hand, so the order of events is the test's own."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.identity :as identity]
   [cljs.test :refer-macros [deftest is]]
   [db.pouch :as pouch]
   [goog.functions :as gfn]
   [sync :as sut]))


(defn- change
  "A change-feed event as PouchDB delivers it."
  [id rev]
  #js {:changes #js [#js {:rev rev}] :id id})


(defn- ^:async with-sync!
  "Starts sync for a stored account, online, with replication answered by
   `sync-once!`, and hands `f` the request and a way to play a change-feed
   event. The write throttle passes every call straight through, so an event
   is judged when it is played.

   The navigator is the test's own, not whatever the runtime has: Node grew a
   global one only in 21, and none of its versions says `onLine`."
  [sync-once! f]
  (let [runtime-navigator (js/Object.getOwnPropertyDescriptor js/globalThis "navigator")
        feed (atom nil)]
    (js/Object.defineProperty js/globalThis
                              "navigator"
                              #js {:configurable true :value #js {:onLine true}})
    (try
      (with-redefs [gfn/throttle           (fn [g _] g)
                    identity/load-identity! (fn [] (js/Promise.resolve {:id "account" :token "t"}))
                    identity/use-identity! (fn [_] nil)
                    pouch/on-change        (fn [_ _ handler]
                                             (reset! feed handler)
                                             (fn [] nil))
                    pouch/sync-once!       sync-once!]
        (let [{:sync/keys [pull!]} (await (sut/start! {:db {}}))]
          (await (f {:feed!    #(@feed (change %1 %2))
                     :request! pull!}))))
      (finally
       (if runtime-navigator
         (js/Object.defineProperty js/globalThis "navigator" runtime-navigator)
         (js/Reflect.deleteProperty js/globalThis "navigator"))))))


(defn- held-passes
  "A fake replication whose passes wait for the test. `started` counts them;
   `finish!` resolves the oldest unfinished one with `result`."
  []
  (let [started (atom 0)
        waiting (atom [])]
    {:finish!   (fn [result]
                  (let [[resolve & more] @waiting]
                    (reset! waiting (vec more))
                    (resolve result)))
     :started   started
     :sync-once (fn [_ _ _]
                  (swap! started inc)
                  (js/Promise. (fn [resolve] (swap! waiting conj resolve))))}))


(defn- settled
  "A promise that settles after everything already queued, timers included."
  []
  (js/Promise. (fn [resolve] (js/setTimeout resolve 0))))


(defn- answered
  "What `p` resolves with, or ::unanswered if it has not within 200 ms. A run
   left waiting on a pass nobody finishes would otherwise never settle, and
   the node runner exits 0 on an empty event loop, test unreported."
  [p]
  (js/Promise.race
   #js [p (js/Promise. (fn [resolve] (js/setTimeout #(resolve ::unanswered) 200)))]))


(def ^:private quiet-pass
  {:pulled 0 :pulled-ids {} :pulled-revs #{} :pushed 1})


(deftest requests-at-once-run-one-pass
  (async-testing "GH-319: route entry, online, poke and a write at once run one replication"
    (let [{:keys [finish! started sync-once]} (held-passes)]
      (await
       (with-sync!
        sync-once
        (^:async fn
         [{:keys [request!]}]
         (let [answers (into-array [(request!) (request! :poke) (request! :poke) (request!)])]
           (await (settled))
           (is (= 1 @started) "the three that came with it are in that pass")
           (finish! quiet-pass)
           (is (= (repeat 4 quiet-pass)
                  (vec (await (answered (js/Promise.all answers)))))
               "every caller hears the one pass")
           (is (= 1 @started) "and no pass follows it"))))))))


(deftest a-request-during-a-pass-gets-exactly-one-more
  (async-testing "GH-319: however many ask during a pass, one pass follows it"
    (let [{:keys [finish! started sync-once]} (held-passes)]
      (await
       (with-sync!
        sync-once
        (^:async fn
         [{:keys [request!]}]
         (let [answer (request!)]
           (await (settled))
           (finish! quiet-pass)
           (is (= {:pulled 0 :pulled-ids {} :pulled-revs #{} :pushed 1} (await (answered answer)))
               "nothing asked during it: the run is one pass")
           (is (= 1 @started))
           (let [first-run (request!)]
             (await (settled))
             (request!)
             (request!)
             (request!)
             (finish! {:pulled      1
                       :pulled-ids  {"vocab" #{"vocab:hund"}}
                       :pulled-revs #{["vocab:hund" "1-a"]}
                       :pushed      0})
             (await (settled))
             (is (= 3 @started) "three requests during the pass, one pass after it")
             (finish! quiet-pass)
             (is (= 1 (:pulled (await (answered first-run))))
                 "a follow-up that pulled nothing does not hide the pass before it that did")
             (is (= 3 @started))))))))))


(deftest a-failed-pass-does-not-hold-the-next
  (async-testing "GH-319: a pass that throws still lets the next request run one"
    (let [started (atom 0)]
      (await
       (with-sync!
        (fn [_ _ _]
          (swap! started inc)
          (js/Promise.reject (js/Error. "replication blew up")))
        (^:async fn
         [{:keys [request!]}]
         (is (nil? (await (answered (request! :poke)))) "a failed run answers nil, as a failed pass does")
         (is (nil? (await (answered (request! :poke)))))
         (is (= 2 @started) "the second request was not handed the dead one")))))))


(deftest the-pull-s-own-writes-request-nothing
  (async-testing "GH-319: a revision the pass pulled is not a local write"
    (let [{:keys [finish! started sync-once]} (held-passes)]
      (await
       (with-sync!
        sync-once
        (^:async fn
         [{:keys [feed! request!]}]
         (let [answer (request!)]
           (await (settled))
           ;; What the pull writes reaches the change feed while it runs.
           (feed! "vocab:hund" "1-a")
           (feed! "vocab:katze" "2-b")
           (finish! {:pulled      2
                     :pulled-ids  {"vocab" #{"vocab:hund" "vocab:katze"}}
                     :pulled-revs #{["vocab:hund" "1-a"] ["vocab:katze" "2-b"]}
                     :pushed      0})
           (await (answered answer))
           (is (= 1 @started) "no pass for what the pass itself brought")
           ;; And a notification that arrives after the pass is over.
           (feed! "vocab:maus" "1-c")
           (await (settled))
           (is (= 2 @started) "a revision no pass pulled is a local write, and asks for one")
           (finish! quiet-pass)
           (await (settled)))))))))


(deftest a-write-during-a-pass-gets-its-own
  (async-testing "GH-319: a local write while a pass runs is pushed by the pass after it"
    (let [{:keys [finish! started sync-once]} (held-passes)]
      (await
       (with-sync!
        sync-once
        (^:async fn
         [{:keys [feed! request!]}]
         (let [answer (request!)]
           (await (settled))
           (feed! "vocab:hund" "1-a")
           (feed! "review-1" "1-local")
           (is (= 1 @started) "judged when the pass ends, not while it runs")
           (finish! {:pulled      1
                     :pulled-ids  {"vocab" #{"vocab:hund"}}
                     :pulled-revs #{["vocab:hund" "1-a"]}
                     :pushed      0})
           (await (settled))
           (is (= 2 @started))
           (finish! quiet-pass)
           (await (answered answer))
           (is (= 2 @started)))))))))
