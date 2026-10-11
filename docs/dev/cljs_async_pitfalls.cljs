(ns cljs-async-pitfalls
  "Implicit awaits inside ^:async. See cljs-async-pitfalls.md.

   Results measured on two compilers:
     shadow-cljs 3.4.12            — what the app builds with
     ClojureScript 1.12.145 (cljs) — cljs.build.api, no shadow
   \"=> 5\"       — the promise was awaited implicitly; the let got the value
   \"=> Promise\" — the let got the promise itself")


(defn delayed
  [val]
  (js/Promise. (fn [resolve] (resolve val))))


(def port
  {:port/get (fn [& _] (delayed 5))})


;; --- explicit await ---------------------------------------------------------


(defn ^:async f
  []
  (let [val (await (delayed 5))]
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => 5


;; --- a call of a defn: not awaited ------------------------------------------


(defn ^:async g
  []
  (let [val (delayed 5)]
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async g2
  []
  (let [val (delayed (inc 1))] ; expression argument, fn from defn
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


;; --- forms that compile to a JS expression: not awaited ---------------------


(defn ^:async c
  []
  (let [val (cond
              :else (delayed 5))]
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async c2
  []
  (let [val (if true (delayed 5) nil)] ; ternary
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async c3
  []
  (let [val (when true (delayed 5))]
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async c4
  []
  (let [val (do (delayed 5))]
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


;; --- forms that compile to a JS statement: awaited implicitly ---------------
;; Both compilers wrap them in (await (async function(){...})()).


(defn ^:async h
  []
  (let [val (case 1
              1 (delayed 5))] ; switch
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => 5


(defn ^:async l
  []
  (let [val (try
              (delayed 5)
              (catch :default _ nil))]
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => 5


(defn ^:async l2
  []
  (let [val (try
              (delayed 5)
              (finally nil))]
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => 5


(defn ^:async l3
  []
  (let [val (try
              (delayed 5))] ; try without catch/finally is dropped
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async t
  []
  (let [val (let [x 5] ; let in value position
              (delayed x))]
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => 5


;; --- a call of a function not named by a defn -------------------------------
;; The compilers differ here. shadow-cljs picks the arity at run time:
;;   (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(G) : f.call(null, G))
;; The argument appears twice, so an expression argument is first stored in a
;; temp var G. That needs a var statement, hence a wrapper function, which
;; ^:async awaits. A literal or a name can be repeated safely, so no wrapper.
;; A fn taken from a map ((:k m)) is wrapped the same way, for the fn itself.
;; Plain ClojureScript calls .call directly.


(defn ^:async p
  []
  (let [val ((:port/get port))] ; fn from a map
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => Promise


(defn ^:async p2
  []
  (let [{:port/keys [get]} port
        val (get)] ; local name, no args
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async p3
  []
  (let [{:port/keys [get]} port
        x   5
        val (get x)] ; local name, literal or name arg
    (println "val = " val)))


;; shadow-cljs => Promise
;; cljs        => Promise


(defn ^:async p4
  []
  (let [{:port/keys [get]} port
        val (get (inc 1))] ; local name, expression arg
    (println "val = " val)))


;; The argument's value does not matter: any expression, (inc 1) or [1 2], is
;; stored in a temp var first.
;; shadow-cljs => 5
;; cljs        => Promise


(defn ^:async p5
  []
  (let [{:port/keys [get]} port
        val (get [1 2])] ; local name, vector arg
    (println "val = " val)))


;; shadow-cljs => 5
;; cljs        => Promise
