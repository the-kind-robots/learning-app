(ns client.support.document
  "A page document for Node, which has none: enough of one for code that
   listens to `visibilitychange`.")


(defn ^:async with-document
  "Calls `f` with a document installed as `document` for the time of the
   promise `f` returns. The document is visible; a test changes
   `visibilityState` and dispatches `visibilitychange` itself."
  [f]
  (let [document (doto (js/EventTarget.) (aset "visibilityState" "visible"))]
    (set! (.-document js/globalThis) document)
    (try
      (await (f document))
      (finally
       (js/Reflect.deleteProperty js/globalThis "document")))))
