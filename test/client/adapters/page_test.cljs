(ns client.adapters.page-test
  "`until-active`: a wait a signal can cancel, with no listener left behind."
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [adapters.page :as page]
   [cljs.test :refer-macros [deftest is]]))


(defn- ^:async with-hidden-page
  "Calls `f` with an atom counting the page listeners alive, while the page
   is hidden, and awaits it. The globals are put back after."
  [f]
  (let [live   (atom 0)
        target (fn [state]
                 #js {:addEventListener    (fn [& _] (swap! live inc))
                      :removeEventListener (fn [& _] (swap! live dec))
                      :visibilityState     state})
        saved  {:document (.-document js/globalThis)
                :window   (.-window js/globalThis)}
        nav    (js/Object.getOwnPropertyDescriptor js/globalThis "navigator")]
    (set! (.-document js/globalThis) (target "hidden"))
    (set! (.-window js/globalThis) (target nil))
    (js/Object.defineProperty js/globalThis "navigator"
                              #js {:value #js {:onLine true} :configurable true :writable true})
    (try
      (await (f live))
      (finally
       (set! (.-document js/globalThis) (:document saved))
       (set! (.-window js/globalThis) (:window saved))
       (when nav
         (js/Object.defineProperty js/globalThis "navigator" nav))))))


(deftest until-active-leaves-no-listener-when-the-signal-has-aborted
  (async-testing "an already aborted signal, and one that aborts later, both leave nothing"
    (await
     (with-hidden-page
      (^:async fn
       [live]
       (let [aborted (js/AbortController.)]
         (.abort aborted)
         (page/until-active (.-signal aborted))
         (is (zero? @live) "already aborted"))
       (let [later (js/AbortController.)]
         (page/until-active (.-signal later))
         (is (pos? @live) "waiting")
         (.abort later)
         (is (zero? @live) "aborted later")))))))
