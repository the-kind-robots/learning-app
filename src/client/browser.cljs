(ns browser
  "Giving the page a turn.")


(defn yield
  "A promise that resolves in a later task, so the page can paint and take input."
  []
  (js/Promise. (fn [resolve] (js/setTimeout resolve 0))))
