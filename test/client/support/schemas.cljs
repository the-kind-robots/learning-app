(ns client.support.schemas
  "The schema list `main` hands the engine, for test databases."
  (:require
   [adapters.collections :as collections]
   [adapters.examples :as examples]
   [adapters.reviews :as reviews]
   [adapters.words :as words]
   [tasks :as tasks]))


(def all
  [words/schema
   reviews/schema
   collections/schema
   examples/schema
   tasks/schema])
