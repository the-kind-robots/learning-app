(ns ports.sharing
  "Handing a picture out of the app: to the system's share sheet, or as a download. `adapters.sharing` does the
   work."
  (:require
   [adapters.sharing :as sharing]))


(defprotocol Sharing
  (share-picture!
    [s file-name svg]
    "Paints `svg`, SVG hiccup in the page's CSS (HTML in a `<foreignObject>` included), three times its size, as a
     PNG named `file-name` and offers it to the share sheet, or downloads it where the browser cannot share files."))


(defn start!
  []
  (reify Sharing
    (share-picture! [_ file-name svg] (sharing/share-picture! file-name svg))))
