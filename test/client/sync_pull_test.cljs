(ns client.sync-pull-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.identity :as identity]
   [cljs.test :refer-macros [deftest is testing]]
   [db.pouch :as pouch]
   [sync :as sut]))


(deftest a-navigation-soon-after-a-pass-runs-no-pass
  (testing "within the interval, nothing written locally: no pass"
    (is (false? (sut/pull-due? {:reason nil :dirty? false :last-pass-ms 100000 :now-ms 110000}))))
  (testing "the interval has passed"
    (is (true? (sut/pull-due?
                {:reason nil :dirty? false :last-pass-ms 100000 :now-ms (+ 100000 sut/pass-interval-ms)}))))
  (testing "no pass yet"
    (is (true? (sut/pull-due? {:reason nil :dirty? false :last-pass-ms nil :now-ms 5000})))))


(deftest a-poke-or-a-local-write-always-passes
  (is (true? (sut/pull-due? {:reason :poke :dirty? false :last-pass-ms 100000 :now-ms 100500})))
  (is (true? (sut/pull-due? {:reason nil :dirty? true :last-pass-ms 100000 :now-ms 100500}))))


(defn- ^:async with-sync!
  "Starts sync for a stored account, online, with the replication answered by
   `sync-once!` and the change feed inert, and hands `f` the handle.

   The navigator is the test's own, not whatever the runtime has: Node grew a
   global one only in 21, and none of its versions says `onLine`."
  [sync-once! f]
  (let [runtime-navigator (js/Object.getOwnPropertyDescriptor js/globalThis "navigator")]
    (js/Object.defineProperty js/globalThis
                              "navigator"
                              #js {:configurable true :value #js {:onLine true}})
    (try
      (with-redefs [identity/load-identity! (fn [] (js/Promise.resolve {:id "account" :token "t"}))
                    identity/use-identity!  (fn [_] nil)
                    pouch/on-change         (fn [_ _ _] (fn [] nil))
                    pouch/sync-once!        sync-once!]
        (await (f (await (sut/start! {:db {}})))))
      (finally
       (if runtime-navigator
         (js/Object.defineProperty js/globalThis "navigator" runtime-navigator)
         (js/Reflect.deleteProperty js/globalThis "navigator"))))))


(deftest pulls-during-a-pass-join-it
  (async-testing "GH-319: route entry, online, poke and push at once run one replication"
    (let [passes (atom 0)
          finish (atom nil)]
      (await
       (with-sync!
        (fn [_ _ _]
          (swap! passes inc)
          (js/Promise. (fn [resolve] (reset! finish resolve))))
        (^:async fn
         [{:sync/keys [pull!]}]
         (let [answers [(pull!) (pull! :poke) (pull! :poke) (pull!)]]
           (is (= 1 @passes) "the other three joined the pass in flight")
           (@finish {:pulled 0 :pulled-ids {} :pushed 1})
           (is (= (repeat 4 {:pulled 0 :pulled-ids {} :pushed 1})
                  (vec (await (js/Promise.all (into-array answers)))))
               "and every caller hears the one pass's result")
           (let [next-pass (pull! :poke)]
             (is (= 2 @passes) "a pull after it ends starts a pass of its own")
             (@finish nil)
             (await next-pass)))))))))


(deftest a-failed-pass-does-not-hold-the-guard
  (async-testing "GH-319: a pass that rejects still lets the next one run"
    (let [passes (atom 0)]
      (await
       (with-sync!
        (fn [_ _ _]
          (swap! passes inc)
          (js/Promise.reject (js/Error. "replication blew up")))
        (^:async fn
         [{:sync/keys [pull!]}]
         (try
           (await (pull! :poke))
           (is false "should have rejected")
           (catch :default err
             (is (= "replication blew up" (ex-message err)))))
         (try (await (pull! :poke)) (catch :default _ nil))
         (is (= 2 @passes) "the second pull was not handed the dead one")))))))
