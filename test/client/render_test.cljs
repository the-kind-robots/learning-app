(ns client.render-test
  "Pins the render scheduling contract from GH-176: one render per dispatch
   that changed the store, zero for a dispatch that changed nothing, and
   immediate renders for writes outside any dispatch."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [application :as application]
   [cljs.test :refer-macros [deftest is testing use-fixtures]]
   [nexus.registry :as nxr]))


(defonce ^:private store (atom {}))


(defonce ^:private renders (atom 0))


(defonce ^:private install-render-once
  (delay (nxr/register-system->state! #(-> % :store deref))
         (application/install-render! store (fn [_] (swap! renders inc)))))


(nxr/register-effect! ::touch-nothing
  (fn touch-nothing [_ _]))


(nxr/register-effect! ::save-nested
  (fn save-nested [{:keys [dispatch]} _ m]
    (dispatch [[:effect/save m]])))


(nxr/register-effect! ::save-later
  (fn save-later [{:keys [dispatch]} _ m]
    (-> (js/Promise.resolve)
        (.then #(dispatch [[:effect/save m]])))))


(use-fixtures :each
              {:before (fn []
                         (force install-render-once)
                         (reset! store {})
                         (reset! renders 0))})


(defn- dispatch!
  [actions]
  (nxr/dispatch {:store store} {} actions))


(deftest every-state-change-renders
  (testing
    "three distinct saves render three times — the accepted cost of the
            compare-only scheme (#213): diffing, never extra paints"
    (dispatch! [[:effect/save {:a 1}]
                [:effect/save {:b 2}]
                [:effect/save {:c 3}]])
    (is (= 3 @renders))
    (is (= {:a 1 :b 2 :c 3} @store))))


(deftest an-identical-save-renders-nothing
  (testing
    "saving the value already in place is free: assoc hands back the
            identical map and the watch sees no change"
    (dispatch! [[:effect/save {:a 1}]])
    (dispatch! [[:effect/save {:a 1}]])
    (is (= 1 @renders))))


(deftest stateless-dispatch-renders-nothing
  (testing "a dispatch that saves nothing renders nothing"
    (dispatch! [[::touch-nothing]])
    (is (= 0 @renders))))


(deftest nested-dispatch-renders-per-change
  (testing "a nested dispatch's save is a change of its own"
    (dispatch! [[:effect/save {:outer 1}]
                [::save-nested {:nested 2}]])
    (is (= 2 @renders))
    (is (= {:outer 1 :nested 2} @store))))


(deftest external-write-renders
  (testing "a swap! outside any dispatch renders immediately"
    (swap! store assoc :external 1)
    (is (= 1 @renders))))


(deftest async-continuation-renders-on-its-own
  (async-testing "an async continuation is a new top-level dispatch with its own render"
    (dispatch! [[:effect/save {:first 1}]
                [::save-later {:second 2}]])
    (is (= 1 @renders))
    (await (js/Promise.resolve))
    (is (= 2 @renders))
    (is (= {:first 1 :second 2} @store))))
