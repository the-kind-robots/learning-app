(ns ports.examples
  "Example fetching as the use cases reach it: one request.
   `adapters.example-fetch` does the work."
  (:require
   [adapters.example-fetch :as example-fetch]))


(defn start!
  [_deps]
  {:examples/fetch example-fetch/fetch-one})
