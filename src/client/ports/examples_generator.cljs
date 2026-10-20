(ns ports.examples-generator
  "Asking the backend for an example: one request.
   `adapters.examples-generator` does the work."
  (:require
   [adapters.examples-generator :as examples-generator]))


(defn start!
  "The function that asks for an example: `(f subject signal)` resolves with `{:example example}` or `{:failure kind}`."
  []
  examples-generator/fetch-one)
