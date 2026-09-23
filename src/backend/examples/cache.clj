(ns examples.cache
  "The store of examples already generated, keyed by the question that produced
   one. A generated sentence depends on nothing but its question — the German
   word, the confirmed Russian glosses, the optional collection context — so
   one row answers every later device and account that asks the same question.

   Examples live in the client's device-db, which has no copy on the server;
   without this table every device pays the provider for a sentence another
   device already has.

   Nothing here refuses a request. The question arrives normalized
   (`examples/question`) and the example arrives already generated and already
   paid for, so the only failure this namespace knows is a database that is
   away: a read that fails is a miss, a write that fails is a dropped row."
  (:require
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [examples :as examples]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [taoensso.telemere :as t]
   [utils :as utils]))


(set! *warn-on-reflection* true)


(defn digest
  "The key of a normalized question, under the generation it would be answered
   by. A digest rather than the composed string: the string's length is
   unbounded, a digest indexes at a fixed cost.

   What the sentence depends on is all of it: the question, and the generation
   that would answer it — the prompt, the models, and what the dictionary says
   about the word (`examples/generation-digest`). Edit the prompt, move to
   another model, or let the word arrive in the dictionary, and every later
   question is a miss. That is the whole invalidation there is, and the only
   one there needs to be.

   The parts are encoded as JSON rather than printed. A separator can be typed
   — a gloss holding one would compose the key of a question with two glosses —
   and both JSON and `pr-str` escape what would otherwise imitate structure,
   but `pr-str` answers differently under a bound `*print-length*` or
   `*print-level*`: two different questions truncated to the same prefix would
   share a key.

   What a question is `examples/question-schema` says, and only
   `examples/question` builds one — the shape is held by that pairing and by
   the tests over it, not by a check here."
  [question]
  (utils/sha256-hex
   (cheshire/generate-string
    [(:word question)
     (:translations question)
     (:context question)
     (examples/generation-digest question)])))


(defn- unavailable!
  "A failing cache is a cache that answers nothing. The table is an
   optimization over a provider that is still there, so a broken read must
   read as a miss and a broken write must be dropped — never a request
   refused after the generation was already paid for."
  [operation error]
  (t/log!
   {:level :warn
    :id    ::unavailable
    :data  {:operation operation :error (ex-message error)}}
   "Example cache unavailable")
  nil)


(defn lookup
  "The example stored for `question`, as data, or nil.

   Nil covers every way there is nothing to serve: no row, a table that cannot
   be read, and a row that does not parse. A row that does not parse cannot be
   repaired from here — `store!` never replaces one — so reading it as a miss
   is what keeps the pair answerable at all."
  [db question]
  (let [question-key (digest question)]
    (try
      (some-> (jdbc/execute-one! db
                ["SELECT example FROM example_cache WHERE question_sha256 = ?" question-key]
                {:builder-fn result-set/as-unqualified-kebab-maps})
              :example
              (cheshire/parse-string true))
      (catch Exception error
        (unavailable! :lookup error)))))


(defn store!
  "Keeps `example` — the map the endpoint serves — under `question`, serialized
   here so that what goes in and what comes out are the same data. A row that
   is already there wins: two processes may generate the same question at once,
   and either answer is as good as the other.

   The `word`, `translations` and `context` columns carry the normalized parts
   the key was built from, so the accumulated cache can be read by hand. Only
   `question_sha256` identifies a question; the readable columns do not.

   A write that fails is dropped: what it would have saved for later has
   already been generated and is about to be served."
  [db question example]
  (try
    (jdbc/execute-one! db
      ["INSERT INTO example_cache (question_sha256, word, translations, context, example)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (question_sha256) DO NOTHING"
       (digest question)
       (:word question)
       (str/join ", " (:translations question))
       (:context question)
       (cheshire/generate-string example)])
    (catch Exception error
      (unavailable! :store error))))
