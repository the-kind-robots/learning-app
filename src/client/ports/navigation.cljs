(ns ports.navigation
  "The browser history, written from the stack of the open windows. `adapters.navigation` does the work."
  (:require
   [adapters.navigation :as navigation]))


(defprotocol Navigation
  (record!
    [n from to]
    "Makes the history end at the stack `to`, which follows `from`: a window more pushes an entry, fewer step back.")
  (follow!
    [n on-windows]
    "Calls `on-windows` with the stack of the entry the app opens on, then with that of every entry the browser steps to, the app's own step back included."))


(defn start!
  "The navigation over `paths`, the path of each page, and the `home` page at the bottom of every stack. It reads
   the address later, with `follow!`."
  [routing]
  (reify Navigation
    (record! [_ from to] (navigation/record! routing from to))
    (follow! [_ on-windows] (navigation/follow! routing on-windows))))
