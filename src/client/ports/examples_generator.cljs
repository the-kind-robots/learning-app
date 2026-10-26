(ns ports.examples-generator
  "Asking the backend for an example: one request, or for another one.
   `adapters.examples-generator` does the work."
  (:require
   [adapters.examples-generator :as examples-generator]))


(defprotocol ExamplesGenerator
  (fetch
    [g subject signal]
    "A promise of `{:example example}` for `subject`, or `{:failure kind}`; `signal` aborts the request.")
  (regenerate
    [g subject avoid signal]
    "A promise of `{:example example}` for `subject` that is none of the sentences in `avoid`, the answer for
     this learner alone, or `{:failure kind}`; `signal` aborts the request."))


(defn start!
  []
  (reify ExamplesGenerator
    (fetch [_ subject signal] (examples-generator/fetch-one subject signal))
    (regenerate [_ subject avoid signal] (examples-generator/request-another subject avoid signal))))
