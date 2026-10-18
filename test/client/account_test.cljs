(ns client.account-test
  "The account is initialized once the learner's memory is loaded and the first replication pass is done, or a
   minute after memory loaded (`ports.account/initialization`)."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.learner :as adapter]
   [adapters.learner.memory :as memory]
   [client.support.wait :as wait]
   [cljs.test :refer-macros [deftest is]]
   [ports.account :as account]
   [ports.learner :as learner]
   [ports.world :as world]))


(defn- over
  "A Learner over `store`, an atom of the learner's state: it tells its status and calls back on change."
  [store]
  (reify learner/Learner
    (on-change [_ f] (adapter/on-change {:store store} f))
    (status [_] (adapter/status {:store store}))))


(defn- sleeping
  "A world whose clock sleeps until `wake!`, with the milliseconds it was asked to sleep and whether its signal
   aborted."
  []
  (let [wake    (atom nil)
        slept   (atom nil)
        aborted (atom false)]
    {:aborted? aborted
     :slept    slept
     :wake!    #(@wake nil)
     :world    (reify world/World
                 (clock [_ _ ms ^js signal]
                   (reset! slept ms)
                   (js/Promise.
                    (fn [resolve]
                      (reset! wake resolve)
                      (.addEventListener signal "abort" #(reset! aborted true))))))}))


(defn- ^:async settled
  "Whether `promise` has resolved by the time the event loop has turned."
  [promise]
  (let [done (atom false)]
    (.then promise #(reset! done true))
    (await (wait/settled))
    @done))


(deftest initialized-waits-for-memory-and-the-first-pass-and-drops-the-timer
  (async-testing "memory loaded, first pass pending: not yet; the pass completes: initialized, the minute's sleep cancelled"
    (let [store                   (atom {:learner/memory memory/empty-memory})
          {:keys [aborted? slept world]} (sleeping)
          [first-pass pass-done!] (let [resolve (atom nil)
                                        promise (js/Promise. #(reset! resolve %))]
                                    [promise @resolve])
          initialized             (account/initialization (over store) world first-pass)]
      (is (false? (await (settled initialized))) "memory is not loaded")
      (swap! store assoc :learner/loaded? true)
      (is (false? (await (settled initialized))) "the first pass is pending")
      (is (= 60000 @slept) "a minute is the longest it waits")
      (pass-done! nil)
      (is (true? (await (settled initialized))))
      (is (true? @aborted?) "the sleep is cancelled when the pass won"))))


(deftest initialized-stops-waiting-for-a-first-pass-after-a-minute
  (async-testing "a first pass that never comes: initialized when the minute is up"
    (let [store                 (atom {:learner/memory memory/empty-memory :learner/loaded? true})
          {:keys [wake! world]} (sleeping)
          initialized           (account/initialization (over store) world (js/Promise. (fn [_])))]
      (is (false? (await (settled initialized))))
      (wake!)
      (is (true? (await (settled initialized)))))))
