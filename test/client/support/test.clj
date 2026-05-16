(ns client.support.test)


(defmacro async-testing
  "Runs async test body with a label, reporting failures and always calling done."
  [label & body]
  (let [done-sym (gensym "done")]
    `(cljs.test/async ~done-sym
       (cljs.test/testing ~label
         (-> ((^:async fn [] ~@body))
             (.catch (fn [error#]
                       (cljs.test/is false (str ~label " failed: " error#))))
             (.finally ~done-sym))))))
