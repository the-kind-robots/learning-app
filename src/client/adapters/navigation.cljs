(ns adapters.navigation
  "The only writer of the browser history. Each history entry holds the stack of the windows open at it, home at
   its bottom (ADR-0026), so Back shows the entry beneath with the stack it holds, and Back on home leaves the
   app."
  (:require
   [cljs.reader :as reader]
   [clojure.set :as set]))


(defn record!
  "Makes the history end at the stack `to`, which follows `from`: a window more pushes an entry, fewer step back."
  [{:keys [paths]} from to]
  (let [step (- (count to) (count from))]
    (cond
      (pos? step) (js/history.pushState (pr-str to) "" (get paths (peek to)))
      (neg? step) (js/history.go step))))


(defn follow!
  "Calls `on-windows` with the stack of the entry the app opens on, then with that of every entry the browser
   steps to, the app's own step back included."
  [{:keys [home paths] :as routing} on-windows]
  (if (string? js/history.state)
    (on-windows (reader/read-string js/history.state))
    ;; Opened by its address: its entry becomes home's and the page opens over it. The query and the fragment,
    ;; an incoming credential among them, leave the address.
    (let [page    (get (set/map-invert paths) js/location.pathname home)
          windows (if (= home page)
                    [home]
                    [home page])]
      (js/history.replaceState (pr-str [home]) "" (get paths home))
      (record! routing [home] windows)
      (on-windows windows)))
  (js/window.addEventListener "popstate"
                              (fn [^js event]
                                (on-windows (reader/read-string (.-state event))))))
