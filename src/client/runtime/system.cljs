(ns runtime.system
  (:require
   [clojure.set :as set]))


;;
;; Lifecycle manager
;;


(defn resolve-dependencies
  "Returns ordered layers [[k ...] ...]; each layer's :requires are
   satisfied by previous layers. Throws on missing deps or cycles."
  [system-definition]
  (let [;; both :requires values and :after keys constrain ordering — unify into one dep set per component
        deps-graph   (update-vals
                      system-definition
                      (fn [{:keys [requires after]}]
                        (-> #{}
                            (into after)
                            (into (vals requires)))))
        deps-set     (reduce into #{} (vals deps-graph))
        ;; fail before starting anything if a referenced component has no definition
        deps-missing (set/difference deps-set (set (keys deps-graph)))]

    (when (seq deps-missing)
      (throw (ex-info "Missing dependency definitions" {:missing deps-missing})))

    ;; Kahn's algorithm: each iteration peels one ready layer and adds it to resolved
    (loop [layers    []
           resolved  #{}
           remaining deps-graph]
      (if (empty? remaining)
        layers
        (let [;; components whose entire dep set is already resolved can start together
              roots (keep (fn [[k deps]]
                            (when (set/subset? deps resolved) k))
                          remaining)]
          (if (seq roots)
            (recur (conj layers roots)
                   (into resolved roots)
                   (reduce dissoc remaining roots))
            (throw (ex-info "Dependency cycle detected"
                            {:remaining (keys remaining)}))))))))


(defn compile-plan
  "Returns ordered layers of [component-key component-definition] entries."
  [system-definition]
  (let [layers (resolve-dependencies system-definition)]
    (mapv (fn [layer]
            (mapv (fn [k] (find system-definition k)) layer))
          layers)))


(defn dependency-args
  "Builds the local dependency map expected by a component start function.
   `requires` maps local-name -> component-key."
  [values requires]
  (into {}
        (map (fn [[local-name dep-key]]
               [local-name (get values dep-key)]))
        requires))


(defn promise?
  [x]
  (and x (ifn? (.-then x))))


(defn- promise
  ; normalize sync and async start returns to a common Promise shape
  [x]
  (if-not (promise? x)
    (js/Promise.resolve x)
    x))


(defn ^:async stop-one!
  ; swallow errors so one failing stop doesn't prevent the remaining stops from running
  [f]
  (try
    (await (promise (f)))
    (catch :default _ nil)))


(defn- stop-fn
  [stop value]
  (when stop
    #(stop value)))


(defn ^:async start-entry!
  [values [k {:keys [requires start stop]}]]
  (let [local-deps (dependency-args values (or requires {}))
        value      (await (promise (start local-deps)))]
    [k value (stop-fn stop value)]))


(defn- settled-value
  [result]
  (when (= "fulfilled" (.-status result))
    (.-value result)))


(defn- settled-reason
  [result]
  (when (= "rejected" (.-status result))
    (.-reason result)))


(defn ^:async stop-started!
  [stops]
  (doseq [f (reverse (filter some? stops))]
    (await (stop-one! f))))


(defn ^:async stop!
  "Stops all components in reverse order, best-effort."
  [stops]
  (await (stop-started! stops)))


;;
;; App shell
;;


(defn ^:async start!
  "Starts components in dependency order.

   Returns {:values {k value} :stops [f ...] :plan [[[k component] ...] ...]}.
   Components in the same layer start concurrently. On failure, stops
   already-started components in reverse order."
  [system-definition]
  (let [plan (compile-plan system-definition)]
    (loop [remaining plan
           started   {}
           stops     []]
      (if (empty? remaining)
        {:values started :stops stops :plan plan}
        (let [layer   (first remaining)
              ; allSettled instead of all: waits for every entry in the layer regardless of failures,
              ; so we can collect stop fns from the ones that succeeded before re-throwing
              results (await (js/Promise.allSettled
                              (into-array (map #(start-entry! started %) layer))))
              ok      (keep settled-value results)
              errors  (keep settled-reason results)]
          (if (seq errors)
            (do
              ; stop this layer's successes before the already-started ones, maintaining reverse order
              (await (stop-started! (concat (keep #(nth % 2) ok) stops)))
              (throw (first errors)))
            (recur (rest remaining)
                   (into started (map (fn [[k v _]] [k v]) ok))
                   (into stops (keep #(nth % 2) ok)))))))))
