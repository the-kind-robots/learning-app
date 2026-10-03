(ns client.support.schemas
  "The schema list `main` hands the engine, for test databases."
  (:require
   [adapters.learner.documents :as documents]
   [tasks :as tasks]))


(def all
  (conj documents/schemas tasks/schema))
