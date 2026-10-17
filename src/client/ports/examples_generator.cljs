(ns ports.examples-generator
  "Asking the backend for an example: one request.
   `adapters.examples-generator` does the work."
  (:require
   [adapters.examples-generator :as examples-generator]))


(defn start!
  [_deps]
  {:examples-generator/fetch examples-generator/fetch-one})
