(ns db-macros)

(defmacro with-couch-op [_op-id body] `(.then ~body db/couch->clj))
