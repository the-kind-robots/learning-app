(ns ports.navigation
  "Moving between the app's screens: the router and the browser history. `adapters.navigation` does the work."
  (:require
   [adapters.navigation :as navigation]))


(defprotocol Navigation
  (navigate
    [n page]
    "Moves to `page`. A step back enters home first, with the `on-enter` given to `follow!`, so home is on screen in the task of the tap.")
  (back
    [n]
    "Steps back to the screen beneath, as the browser's Back does.")
  (follow!
    [n on-enter]
    "Starts the router and calls `on-enter` with the page of every route entered."))


(defn start!
  "The navigation over `routes`, a table of paths and page names, and the `home` page the history rests on. The
   router starts later, with `follow!`."
  [routing]
  (let [navigator (navigation/navigator routing)]
    (reify Navigation
      (navigate [_ page] (navigation/navigate! navigator page))
      (back [_] (navigation/back!))
      (follow! [_ on-enter] (navigation/follow! navigator on-enter)))))
