(ns dev.kernel-comparison
  "Compares the memory kernels on a learner's review log (#513). The log is user-db as CouchDB lists it,
   `_all_docs?include_docs=true`. Each word's reviews are replayed in time order; before each answer every
   kernel says how likely the word is recalled, from the reviews before it, and is scored against the
   answer. Only answers both kernels can predict count: FSRS-34 knows nothing of a word before its first
   answer.

     npx shadow-cljs compile kernel-comparison
     node target/kernel-comparison.js log.json

   The learner's pace is fitted once, on every timed right answer of the log, the later ones included."
  (:require
   ["fs" :as fs]
   [clojure.string :as str]
   [domain.fsrs :as fsrs]
   [domain.retention :as retention]
   [utils :as utils]))


(defn- answers
  "Every answer of the log `[word reviews]` pairs as `{:outcome :recall-current :recall-fsrs}`, outcome 1 or 0,
   the recalls as probabilities."
  [words reviews-by-word pace]
  (for [[word-id reviews] reviews-by-word
        :let  [word (words word-id)]
        :when word
        [history review] (map vector
                              (reductions retention/with-review retention/empty-reviews reviews)
                              reviews)
        :let  [now-ms         (* 1000 (utils/iso->secs (:created-at review)))
               recall-fsrs    (fsrs/log-recall fsrs/default-weights history word pace now-ms)
               recall-current (retention/log-recall history now-ms)]
        :when (and recall-fsrs recall-current)]
    {:outcome        (if (:retained review) 1 0)
     :recall-current (js/Math.exp recall-current)
     :recall-fsrs    (js/Math.exp recall-fsrs)}))


(defn- log-loss
  [outcomes recalls]
  (/ (reduce +
             (map (fn [outcome recall]
                    (let [p (max 1e-6 (min (- 1 1e-6) recall))]
                      (- (if (== 1 outcome) (js/Math.log p) (js/Math.log (- 1 p))))))
                  outcomes
                  recalls))
     (count outcomes)))


(defn- auc
  "The probability that a recalled answer was predicted likelier than a forgotten one, ties counting half."
  [outcomes recalls]
  (let [ranked    (vec (sort-by first (map vector recalls outcomes)))
        ;; Ranks from 1; a run of equal recalls shares its mean rank.
        ranks     (loop [position 0
                         ranks    []]
                    (if (< position (count ranked))
                      (let [run-end (loop [end position]
                                      (if (and (< (inc end) (count ranked))
                                               (== (first (ranked (inc end))) (first (ranked position))))
                                        (recur (inc end))
                                        end))
                            rank    (/ (+ position run-end 2) 2)]
                        (recur (inc run-end) (into ranks (repeat (inc (- run-end position)) rank))))
                      ranks))
        recalled  (count (filter #(== 1 (second %)) ranked))
        forgotten (- (count ranked) recalled)]
    (/ (- (reduce + (map (fn [rank [_ outcome]] (if (== 1 outcome) rank 0)) ranks ranked))
          (/ (* recalled (inc recalled)) 2))
       (* recalled forgotten))))


(defn- calibration
  "Answers by predicted recall in tenths: `[tenth answers mean-predicted actual]`."
  [outcomes recalls]
  (->> (map vector recalls outcomes)
       (group-by #(min 9 (js/Math.floor (* 10 (first %)))))
       (sort-by key)
       (map (fn [[tenth pairs]]
              [tenth
               (count pairs)
               (/ (reduce + (map first pairs)) (count pairs))
               (/ (reduce + (map second pairs)) (count pairs))]))))


(defn- report
  [kernel outcomes recalls]
  (println (str kernel
                ": log-loss " (.toFixed (log-loss outcomes recalls) 4)
                ", AUC "      (.toFixed (auc outcomes recalls) 4)
                ", mean predicted " (.toFixed (/ (reduce + recalls) (count recalls)) 4)))
  (doseq [[tenth size predicted actual] (calibration outcomes recalls)]
    (println (str "  " (/ tenth 10)
                  "–"  (/ (inc tenth) 10)
                  ": " size
                  " answers, predicted "
                  (.toFixed predicted 3)
                  ", recalled " (.toFixed actual 3)))))


(defn main
  [path]
  (let [docs            (->> (js/JSON.parse (fs/readFileSync path "utf8"))
                             .-rows
                             (keep #(.-doc ^js %))
                             ;; Stored keys are snake case, `_id` aside.
                             (map #(update-keys (js->clj %)
                                                (fn [k]
                                                  (keyword (if (#{"_id" "_rev"} k) k (str/replace k "_" "-")))))))
        words           (->> docs
                             (filter #(= "vocab" (:type %)))
                             (into {} (map (juxt :_id identity))))
        reviews         (->> docs
                             (filter #(= "review" (:type %)))
                             (map #(assoc % :id (:_id %)))
                             (sort-by (juxt #(utils/iso->secs (:created-at %)) :id)))
        reviews-by-word (group-by :word-id reviews)
        pace            (fsrs/pace (for [review reviews
                                         :let   [word (words (:word-id review))]
                                         :when  (and word (:retained review) (number? (:answer-ms review)))]
                                     {:answer-ms (:answer-ms review)
                                      :length    (count (:value word))}))
        scored          (answers words reviews-by-word pace)
        outcomes        (map :outcome scored)]
    (println (str (count words)
                  " words, " (count reviews)
                  " reviews, "
                  (count (filter #(number? (:answer-ms %)) reviews))
                  " timed; pace " (pr-str pace)))
    (println (str (count scored) " answers scored, " (count (filter #(== 1 %) outcomes)) " recalled"))
    (report "current" outcomes (map :recall-current scored))
    (report "FSRS-34" outcomes (map :recall-fsrs scored))))
