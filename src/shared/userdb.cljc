(ns userdb
  "The account ↔ database naming rule of db-per-user (ADR-0006): account N
   owns the server database `userdb-N`. Every builder and parser of that name
   lives here.")


(defn db-name
  [account-id]
  (str "userdb-" account-id))


(defn account-id
  "The account id a `userdb-N` name derives from, or nil for any other
   database."
  [db-name]
  (some-> (re-matches #"userdb-(\d+)" db-name) second parse-long))
