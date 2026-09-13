(ns client.support.schemas
  "The schema list `main` hands the engine, for test databases."
  (:require
   [adapters.collections :as collections]
   [adapters.examples :as examples]
   [adapters.progress-store :as progress-store]
   [tasks :as tasks]))


(def all
  [progress-store/word-schema
   progress-store/review-schema
   progress-store/lesson-schema
   collections/schema
   examples/schema
   tasks/schema])
