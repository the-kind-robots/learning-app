(ns backend.support.generation
  "Canned provider answers and a join counter for the example-generation
   tests."
  (:require
   [cheshire.core :as cheshire]
   [single-flight :as single-flight])
  (:import
   [java.util.concurrent CountDownLatch TimeUnit]))


(set! *warn-on-reflection* true)


(defn answered
  "An http-kit response promise, already delivered with `response`."
  [response]
  (doto (promise)
    (deliver response)))


(defn provider-answering-in-turn
  "An `example-api-request` stub that answers with `responses` in turn, the
   last one repeated. It counts the attempts in `calls`. A Throwable among
   `responses` is a failed request: http-kit delivers it as `{:error e}`."
  [calls responses]
  (fn [& _]
    (let [attempt  (swap! calls inc)
          response (nth responses (min (dec attempt) (dec (count responses))))]
      (answered (if (instance? Throwable response)
                  {:error response}
                  response)))))


(defn completion
  "A provider 200 whose one choice carries `message`."
  ([message]
   (completion message "stop"))
  ([message finish-reason]
   {:status 200
    :body   (cheshire/generate-string
             {:choices [{:finish_reason finish-reason
                         :index         0
                         :message       message}]})}))


(defn example-completion
  "A provider 200 whose message carries `example` as the model's JSON answer."
  [example]
  (completion {:content (cheshire/generate-string example)}))


(def rejected-candidate
  "A completion the checks reject: the asked word is nowhere in it."
  (example-completion {:value       "The dog barks."
                       :translation "Собака лает."
                       :structure   [{:usedForm       "dog"
                                      :dictionaryForm "dog"
                                      :translation    "собака"}]}))


(defn with-joins-counted
  "Calls `(body joined)`, where `(joined)` says whether `expected-joins`
   callers joined a running task within two seconds."
  [expected-joins body]
  (let [latch (CountDownLatch. (int expected-joins))]
    (with-redefs-fn {#'single-flight/joined (fn [_task-id] (.countDown latch))}
      #(body (fn joined []
             (.await latch 2 TimeUnit/SECONDS))))))
