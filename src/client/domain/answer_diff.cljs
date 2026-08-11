(ns domain.answer-diff
  (:require
   [clojure.string :as str]
   [utils :as utils]))


(defn- grading-tokens
  [s]
  (->> (str/split (utils/normalize-for-grading s) #"\s+")
       (remove str/blank?)
       (vec)))


(defn- lcs-lengths
  "dp table where [i j] is the LCS length of (subvec a i) and (subvec b j)."
  [a b]
  (let [n (count a)
        m (count b)]
    (reduce
     (fn [dp i]
       (reduce
        (fn [dp j]
          (assoc-in dp
           [i j]
           (if (= (nth a i) (nth b j))
             (inc (get-in dp [(inc i) (inc j)]))
             (max (get-in dp [(inc i) j])
                  (get-in dp [i (inc j)])))))
        dp
        (range (dec m) -1 -1)))
     (vec (repeat (inc n) (vec (repeat (inc m) 0))))
     (range (dec n) -1 -1))))


(defn- diff-ops
  [a b]
  (let [dp (lcs-lengths a b)]
    (loop [i   0
           j   0
           ops []]
      (cond
        (and (< i (count a))
             (< j (count b))
             (= (nth a i) (nth b j)))
        (recur (inc i) (inc j) (conj ops [:match (nth a i)]))

        (and (< i (count a))
             (or (>= j (count b))
                 (>= (get-in dp [(inc i) j])
                     (get-in dp [i (inc j)]))))
        (recur (inc i) j (conj ops [:answer-only (nth a i)]))

        (< j (count b))
        (recur i (inc j) (conj ops [:expected-only (nth b j)]))

        :else ops))))


(defn- gap-segments
  "One divergence between matches: answer tokens with an expected counterpart
   are :wrong, surplus ones :extra; every unmet expected token is :missing."
  [ops]
  (let [answer-tokens   (into [] (comp (filter #(= :answer-only (first %))) (map second)) ops)
        expected-tokens (into [] (comp (filter #(= :expected-only (first %))) (map second)) ops)
        paired          (min (count answer-tokens) (count expected-tokens))]
    {:answer   (vec (map-indexed (fn [idx token]
                                   {:status (if (< idx paired) :wrong :extra)
                                    :text   token})
                                 answer-tokens))
     :expected (mapv (fn [token] {:status :missing :text token}) expected-tokens)}))


(defn answer-diff
  "Token diff between the user's answer and the expected phrase, both
   grading-normalized so alignment is guaranteed. Returns
   {:answer [{:text :status}] :expected [{:text :status}]} where the answer
   statuses are :match/:wrong/:extra and the expected :match/:missing."
  [answer expected]
  (->> (diff-ops (grading-tokens answer) (grading-tokens expected))
       (partition-by #(= :match (first %)))
       (reduce
        (fn [segments [[op] :as ops]]
          (if (= :match op)
            (reduce (fn [segments [_ token]]
                      (-> segments
                          (update :answer conj {:status :match :text token})
                          (update :expected conj {:status :match :text token})))
                    segments
                    ops)
            (let [gap (gap-segments ops)]
              (-> segments
                  (update :answer into (:answer gap))
                  (update :expected into (:expected gap))))))
        {:answer   []
         :expected []})))
