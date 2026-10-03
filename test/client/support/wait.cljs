(ns client.support.wait
  "Waiting in an async test: for what is already queued, or for a condition.")


(defn settled
  "Resolves after everything queued so far has run, timers included."
  []
  (js/Promise. (fn [resolve] (js/setTimeout resolve 0))))


(defn ^:async until
  "Resolves once `ready?` returns something truthy, or a promise of it. It
   asks every 10 ms and rejects after two seconds."
  [ready?]
  (loop [tries 200]
    (cond
      (await (ready?)) true
      (zero? tries)    (throw (ex-info "condition never met" {}))
      :else            (do (await (js/Promise. (fn [resolve] (js/setTimeout resolve 10))))
                           (recur (dec tries))))))
