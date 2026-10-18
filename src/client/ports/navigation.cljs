(ns ports.navigation
  "Moving between the app's screens: the router and the browser history. `adapters.navigation` does the work."
  (:require
   [adapters.navigation :as adapter]))


(defprotocol Navigation
  (navigate
   [n page]
   "Moves to `page`. A step back enters home first, with the `on-enter` given to `follow!`, so home is on screen in the task of the tap.")
  (follow!
   [n on-enter]
   "Starts the router and calls `on-enter` with the page of every route entered."))


(defn start!
  "The navigation over `routes`, a table of paths and page names, and the `home` page the history rests on. The
   router starts later, with `follow!`."
  [routing]
  (let [navigator (adapter/navigator routing)]
    (reify Navigation
      (navigate [_ page] (adapter/navigate! navigator page))
      (follow! [_ on-enter] (adapter/follow! navigator on-enter)))))
