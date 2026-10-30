(ns grants
  "Single-use grants: the sha256 of an invite. Provisioning burns one to open
   an account."
  (:require
   [next.jdbc :as jdbc]))


(set! *warn-on-reflection* true)


(def ttl-ms
  "How long an invite stays redeemable."
  (* 30 24 60 60 1000))


(defn store!
  "Stores `sha256` as an invite redeemable for `ttl-ms`."
  [db sha256 ttl-ms]
  (jdbc/execute! db
    ["INSERT INTO grants (sha256, expires_at) VALUES (?, ?)"
     sha256 (+ (System/currentTimeMillis) ttl-ms)]))
