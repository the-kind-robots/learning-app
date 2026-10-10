(ns abort
  "Waiting on an AbortSignal without leaving a listener behind.")


(defn on-abort
  "Calls `f` once when `signal` aborts, at once if it already has. A nil
   `signal` never aborts. Returns a function that removes the listener; call
   it when the wait ends some other way."
  [^js signal f]
  (cond
    (nil? signal)
    (fn unlisten [])

    (.-aborted signal)
    (do (f)
        (fn unlisten []))

    :else
    ;; A fresh fn per call: it drops the Event, and two callers passing the same f keep separate listeners.
    (let [listener (fn [_] (f))]
      (.addEventListener signal "abort" listener #js {:once true})
      (fn unlisten []
        (.removeEventListener signal "abort" listener)))))
