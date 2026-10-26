(ns sqlite.application-defined-functions
  (:require
   [clojure.math :as math]
   [clojure.string :as str]))


(defn- current-time
  "Returns current unix time (seconds)"
  []
  (quot (System/currentTimeMillis) 1000))


;;
;; Normalize German
;;


(defn- sanitize-text
  [text]
  (-> text
      (or "")
      (str/replace #"\p{Punct}" " ")
      (str/trim)
      (str/replace #"\s+" " ")))


(defn- normalize-german
  [text]
  (-> text
      (sanitize-text)
      (str/lower-case)
      (str/replace #"ä" "ae")
      (str/replace #"ö" "oe")
      (str/replace #"ü" "ue")
      (str/replace #"ß" "ss")))


(gen-class
 :name    sqlite.application-defined-functions.NormalizeGerman
 :prefix  "normalize-german-"
 :extends org.sqlite.Function
 :exposes-methods {args       _args
                   result     _result
                   value_text _value_text})


(defn- normalize-german-xFunc
  [this]
  (let [text (._value_text this 0)
        text (normalize-german text)]
    (._result this text)))


;;
;; Retention Level
;;


(gen-class
 :name            sqlite.application-defined-functions.RetentionLevel
 :prefix          "retention-level-"
 :extends         org.sqlite.Function$Aggregate
 :exposes-methods {result    _result
                   value_int _value_int}
 :state           state
 :init            init)


;; approx 50% forgetting after 5 min
(def ^:private initial-forgetting-rate 0.00231)


(defn- retention-level
  [{:keys [reviews timestamps created-at]}]
  (let [timedeltas      (map - timestamps (cons created-at timestamps))
        time-since-last-review (- (current-time) (last timestamps))
        forgetting-rate (reduce
                         (fn [forgetting-rate [retained? timedelta]]
                           (if retained?
                             (/ forgetting-rate (+ 1 (* forgetting-rate timedelta)))
                             (* 2 forgetting-rate)))
                         initial-forgetting-rate
                         (map vector reviews timedeltas))]
    (* 100 (math/exp (- (* forgetting-rate time-since-last-review))))))


(defn- retention-level-init
  []
  [[] (atom {:reviews [] :timestamps []})])


(defn- retention-level-xStep
  [this]
  (let [state       (.state this)
        retained?   (pos? (._value_int this 0))
        reviewed-at (._value_int this 1)
        created-at  (._value_int this 2)]
    (swap! state #(-> %
                      (assoc :created-at created-at)
                      (update :reviews conj retained?)
                      (update :timestamps conj reviewed-at)))))


(defn- retention-level-xFinal
  [this]
  (let [state (.state this)
        retention-level (retention-level @state)]
    (reset! state {:reviews [] :timestamps []})
    (._result this retention-level)))
