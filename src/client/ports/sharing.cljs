(ns ports.sharing
  "Handing a picture out of the app: to the system's share sheet, or as a download. `adapters.sharing` does the
   work."
  (:require
   [adapters.sharing :as sharing]))


(defprotocol Sharing
  (share-picture!
    [s picture]
    "Draws `picture` — `:width`, `:height`, `:file-name` and its `:shapes`, rectangles and texts back to front — as
     a PNG and offers it to the share sheet, or downloads it where the browser cannot share files."))


(defn start!
  []
  (reify Sharing
    (share-picture! [_ picture] (sharing/share-picture! picture))))
