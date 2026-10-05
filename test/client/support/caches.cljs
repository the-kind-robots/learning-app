(ns client.support.caches
  "A Cache API for Node, which has none: what `adapters.learner.snapshot`
   reads and writes, kept in a map. One cache holds every entry; the cache
   name is not kept.")


(defn ^:async with-cache-api
  "Calls `f` with a Cache API installed as `caches` for the time of the
   promise `f` returns. `f` gets `{:entries :puts}`: a `js/Map` from key to
   stored text, which a test may read and change, and an atom counting the
   puts."
  [f]
  (let [entries (js/Map.)
        puts    (atom 0)
        cache   #js {:delete (fn [key] (js/Promise.resolve (.delete entries key)))
                     :match  (fn [key]
                               (js/Promise.resolve (when (.has entries key)
                                                     (js/Response. (.get entries key)))))
                     :put    (fn [key ^js response]
                               (.then (.text response)
                                      (fn [text]
                                        (swap! puts inc)
                                        (.set entries key text))))}]
    (set! (.-caches js/globalThis) #js {:open (fn [_name] (js/Promise.resolve cache))})
    (try
      (await (f {:entries entries :puts puts}))
      (finally
       (js/Reflect.deleteProperty js/globalThis "caches")))))
