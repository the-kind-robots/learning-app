(ns backend.examples-test
  (:require
   [cheshire.core :as cheshire]
   [clojure.test :refer [are deftest is testing]]
   [db :as db]
   [examples :as sut]
   [examples.dictionary :as dictionary]
   [org.httpkit.client :as client]))


(def ^:private good-example
  {:value       "Die Leiter steht neben der Wand."
   :translation "Лестница стоит рядом со стеной."
   :structure   [{:usedForm "Leiter" :dictionaryForm "die Leiter" :translation "лестница"}
                 {:usedForm "steht" :dictionaryForm "stehen" :translation "стоять"}
                 {:usedForm "Wand" :dictionaryForm "die Wand" :translation "стена"}]})


(def ^:private too-short-example
  (assoc good-example :value "Die Leiter."))


(defn- chat
  "The provider's answer carrying `example` as the model's message."
  [example]
  {:status 200
   :body   (cheshire/generate-string
            {:choices [{:message {:content (cheshire/generate-string example)}}]})})


(defn- generating
  "Runs (f) with the provider answering each request with the next of
   `responses` (the last repeats) and the dictionary knowing nothing. The only
   things stood in for are the provider's HTTP client and the dictionary
   lookup. Returns what (f) returns."
  [responses f]
  (let [remaining (atom responses)]
    (with-redefs [client/request
                  (fn [_request]
                    (let [response (first @remaining)]
                      (when (next @remaining) (swap! remaining rest))
                      (if (instance? Throwable response)
                        (delay (throw response))
                        (delay response))))
                  dictionary/lookup-dictionary-entries (constantly nil)]
      (f))))


(defn- generate
  [word translation attempts]
  (sut/generate-one! (sut/question {:word word :translation translation}) attempts))


(deftest a-generation-that-fails-transiently-is-retried-to-a-valid-example
  (testing "a provider error, then an invalid example, then a valid one: the valid one is the answer"
    (is (= (#'sut/add-word-indexes good-example)
           (generating [{:status 500 :body "oops"}
                        (chat too-short-example)
                        (chat good-example)]
                       #(generate "Leiter" "лестница" 3)))))
  (testing "a dropped connection is just another failed attempt"
    (is (= (#'sut/add-word-indexes good-example)
           (generating [(ex-info "network down" {:status 0})
                        (chat good-example)]
                       #(generate "Leiter" "лестница" 2)))))
  (testing "when every attempt fails the caller gets no example"
    (is (nil? (generating [(chat too-short-example)] #(generate "Leiter" "лестница" 3))))
    (is (nil? (generating [(ex-info "network down" {:status 0})] #(generate "Hund" "собака" 2))))))


(deftest a-rate-limited-generation-is-not-retried
  (testing "the failure is reported at once, though a valid answer waited behind it"
    (let [result (generating [{:status 429 :body "{\"error\":{\"code\":\"rate_limit_exceeded\"}}"}
                              (chat good-example)]
                             #(generate "Leiter" "лестница" 3))]
      (is (sut/generation-failure? result))
      (is (= 429 (:status result))))))


(deftest a-meta-answer-in-the-wrong-language-is-not-an-example
  (is (nil? (generating [(chat {:value       "The example for 'aufstehen' is ..."
                                :translation "to stand up"
                                :structure   [{:usedForm       "aufstehen"
                                               :dictionaryForm "aufstehen"
                                               :translation    "to stand up"}]})]
                        #(generate "aufstehen" "вставать" 3)))))


(def ^:private dog
  {:value       "Der Hund läuft schnell im Park."
   :translation "Собака быстро бежит по парку."
   :structure   [{:usedForm "Hund" :dictionaryForm "der Hund" :translation "собака"}
                 {:usedForm "läuft" :dictionaryForm "laufen" :translation "бежит"}
                 {:usedForm "schnell" :dictionaryForm "schnell" :translation "быстро"}
                 {:usedForm "Park" :dictionaryForm "der Park" :translation "парк"}]})


(deftest a-malformed-example-is-rejected-whatever-the-flaw
  (are [flaw example] (= :malformed-example (:issue (#'sut/example-issue "Hund" example)))
    "English mixed into the translation"
    (assoc dog :translation "The dog runs quickly in the park, собака.")

    "a small Latin tail in the translation"
    (assoc dog :translation "Собака бежит fast.")

    "Latin in a structure item's translation"
    (assoc-in dog [:structure 2 :translation] "быстро fast")

    "Cyrillic in the German sentence"
    (assoc dog :value "Der Hund бежит по парку.")

    "two sentences"
    (assoc dog :value "Der Hund läuft. Er ist schnell." :translation "Собака бежит. Она быстрая.")

    "a prefixed German explanation"
    (assoc dog :value "Sentence: Der Hund läuft im Park.")

    "a prefixed Russian explanation"
    (assoc dog :translation "Перевод: Собака бежит в парке.")

    "meta text everywhere"
    {:value       "The example for 'aufstehen' is ..."
     :translation "to stand up"
     :structure   [{:usedForm "aufstehen" :dictionaryForm "aufstehen" :translation "to stand up"}]}))


(deftest a-target-named-only-in-the-structure-is-not-in-the-sentence
  (testing "target forms mentioned only in structure do not count as present in the sentence"
    (let [example {:value "Der Hund läuft schnell im Park."
                   :translation "Собака быстро бежит по парку."
                   :structure
                   [{:usedForm       "Leiter"
                     :dictionaryForm "die Leiter"
                     :translation    "лестница"}
                    {:usedForm       "läuft"
                     :dictionaryForm "laufen"
                     :translation    "бежит"}
                    {:usedForm       "schnell"
                     :dictionaryForm "schnell"
                     :translation    "быстро"}
                    {:usedForm       "Park"
                     :dictionaryForm "der Park"
                     :translation    "парк"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "Leiter" example))))))))

(deftest structure-items-out-of-sentence-order-are-rejected
  (testing "structure items must stay in left-to-right sentence order"
    (let [example {:value "Pass auf deine Seele auf."
                   :translation "Береги свою душу."
                   :structure
                   [{:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "Pass"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "Seele"
                     :dictionaryForm "die Seele"
                     :translation    "душа"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :structure-mismatch
               (:issue (#'sut/example-issue "aufpassen" example))))))))

(deftest a-doubled-separable-prefix-is-accepted-and-costs-a-tooltip
  (testing
    "the pair guard is gone: with `structure` annotating words, a doubled separable prefix and a word the sentence genuinely says twice are the same shape"
    (let [example {:value "Pass auf deine Sachen auf!"
                   :translation "Береги свои вещи!"
                   :structure
                   [{:usedForm       "Pass"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}
                    {:usedForm       "Sachen"
                     :dictionaryForm "die Sache"
                     :translation    "вещи"}
                    {:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "беречь"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is
         (nil? (:issue (#'sut/example-issue "aufpassen" example)))
         "the preposition wrongly annotated with the verb's gloss is the cost; rejecting this also rejected every `von Zeit zu Zeit`")
        (is (= [0 1 3 4]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example)))))))))

(deftest a-detached-prefix-at-the-sentence-end-is-indexed
  (testing "backend assigns the final token index for a detached prefix near punctuation"
    (let [example {:value "Pass gut auf das kleine Kind auf!"
                   :translation "Присмотри внимательно за маленьким ребёнком!"
                   :structure
                   [{:usedForm       "Pass"
                     :dictionaryForm "aufpassen"
                     :translation    "присматривать"}
                    {:usedForm       "gut"
                     :dictionaryForm "gut"
                     :translation    "хорошо"}
                    {:usedForm       "Kind"
                     :dictionaryForm "das Kind"
                     :translation    "ребёнок"}
                    {:usedForm       "auf"
                     :dictionaryForm "aufpassen"
                     :translation    "присматривать"}]}]
      (is (= [0 1 5 6]
             (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))))))

(deftest a-multi-word-target-is-found-in-the-sentence
  (testing "`structure` annotates words, so the construction is checked against the sentence"
    (let [example {:value       "Ich komme auf jeden Fall mit."
                   :translation "Я обязательно пойду вместе."
                   :structure   [{:usedForm       "komme"
                                  :dictionaryForm "mitkommen"
                                  :translation    "идти вместе"}
                                 {:usedForm       "Fall"
                                  :dictionaryForm "der Fall"
                                  :translation    "случай"}
                                 {:usedForm       "mit"
                                  :dictionaryForm "mitkommen"
                                  :translation    "идти вместе"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "auf jeden Fall" example))
            "auf, jeden and Fall are all words of the sentence")
        (is (= [1 4 5]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))
            "nothing in the structure says those words were the target")))))

(deftest an-inflected-target-word-is-found-through-its-dictionary-form
  (testing "`verlieren` is in `Er verliert den Kopf.` only through the item that names it"
    (let [example {:value       "Er verliert den Kopf."
                   :translation "Он теряет голову."
                   :structure   [{:usedForm       "verliert"
                                  :dictionaryForm "verlieren"
                                  :translation    "терять"}
                                 {:usedForm       "Kopf"
                                  :dictionaryForm "der Kopf"
                                  :translation    "голова"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "den Kopf verlieren" example)))))))

(deftest a-word-the-sentence-says-twice-no-longer-costs-the-example
  (testing "two identical items are ordinary German, not a mistake to reject"
    (let [example {:value       "Von Zeit zu Zeit besuche ich meine Eltern."
                   :translation "Время от времени я навещаю родителей."
                   :structure   [{:usedForm       "Zeit"
                                  :dictionaryForm "die Zeit"
                                  :translation    "время"}
                                 {:usedForm       "Zeit"
                                  :dictionaryForm "die Zeit"
                                  :translation    "время"}
                                 {:usedForm       "besuche"
                                  :dictionaryForm "besuchen"
                                  :translation    "навещать"}
                                 {:usedForm       "Eltern"
                                  :dictionaryForm "die Eltern"
                                  :translation    "родители"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "von Zeit zu Zeit" example)))
        (is (= [1 3 4 7]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example))))
            "each occurrence indexes its own position")))))

(deftest a-multi-word-target-missing-from-the-sentence-is-rejected
  (testing "the construction must be in the sentence — every word of it"
    (let [example {:value       "Ich besuche meine Eltern."
                   :translation "Я навещаю своих родителей."
                   :structure   [{:usedForm       "besuche"
                                  :dictionaryForm "besuchen"
                                  :translation    "навещать"}
                                 {:usedForm       "Eltern"
                                  :dictionaryForm "die Eltern"
                                  :translation    "родители"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "von Zeit zu Zeit" example))))))))

(deftest a-partly-present-construction-is-rejected
  (testing "most of the words is not the construction"
    (let [example {:value       "Auf dem Tisch liegt ein Buch."
                   :translation "На столе лежит книга."
                   :structure   [{:usedForm       "Tisch"
                                  :dictionaryForm "der Tisch"
                                  :translation    "стол"}
                                 {:usedForm       "liegt"
                                  :dictionaryForm "liegen"
                                  :translation    "лежать"}
                                 {:usedForm       "Buch"
                                  :dictionaryForm "das Buch"
                                  :translation    "книга"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "auf jeden Fall" example)))
            "`auf` is there, `jeden` and `Fall` are not")))))

(deftest an-article-pair-target-passes-on-the-sentence-when-structure-cannot-name-it
  (testing "`das heißt` is a construction the shape rule reads as an article pair"
    (let [example {:value       "Es regnet, das heißt, wir bleiben zu Hause."
                   :translation "Идёт дождь, то есть мы остаёмся дома."
                   :structure   [{:usedForm       "regnet"
                                  :dictionaryForm "regnen"
                                  :translation    "идти дождю"}
                                 {:usedForm       "heißt"
                                  :dictionaryForm "heißen"
                                  :translation    "значить"}
                                 {:usedForm       "bleiben"
                                  :dictionaryForm "bleiben"
                                  :translation    "оставаться"}
                                 {:usedForm       "Hause"
                                  :dictionaryForm "das Haus"
                                  :translation    "дом"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (nil? (#'sut/example-issue "das heißt" example)))))))

(deftest a-reflexive-dictionary-form-matches-its-bare-verb-target
  (testing "non-article target can match reflexive dictionary form"
    (let [example {:value "Er stellt sich heute freundlich vor."
                   :translation "Он сегодня дружелюбно представляется."
                   :structure
                   [{:usedForm       "stellt"
                     :dictionaryForm "sich vorstellen"
                     :translation    "представляться"}
                    {:usedForm       "heute"
                     :dictionaryForm "heute"
                     :translation    "сегодня"}
                    {:usedForm       "freundlich"
                     :dictionaryForm "freundlich"
                     :translation    "дружелюбно"}
                    {:usedForm       "vor"
                     :dictionaryForm "sich vorstellen"
                     :translation    "представляться"}]}]
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)]
        (is (= nil
               (#'sut/example-issue
                "vorstellen"
                example)))))))

(deftest a-question-reads-what-the-caller-sent
  (testing "one gloss as a bare string"
    (is (= ["собака"] (:translations (sut/question {:word "Hund" :translation "собака"})))))
  (testing "several as a collection, sorted — their order is this device's, not anyone's"
    (is (= ["пёс" "собака"]
           (:translations (sut/question {:word "Hund" :translation ["собака" "пёс"]})))))
  (testing "none at all"
    (is (= [] (:translations (sut/question {:word "Hund"})))))
  (testing "blanks and duplicates are not glosses"
    (is (= ["собака"]
           (:translations (sut/question {:word "Hund" :translation [" собака " "   " "собака"]})))))
  (testing "the word keeps its case and loses its spacing"
    (is (= "Hund" (:word (sut/question {:word " Hund "})))))
  (testing "a blank context is no context"
    (is (nil? (:context (sut/question {:word "Hund" :context "   "}))))
    (is (= "Tiere" (:context (sut/question {:word "Hund" :context " Tiere "}))))))

(deftest a-bare-word-resolves-through-its-surface-form-to-the-lemma-entry
  (testing "bare words can resolve through surface-form docs to article-bearing lemma docs"
    (with-redefs [db/request-sync (fn [request]
                                      (case (:url request)
                                        "dictionary-db/_find"
                                        {:status 200
                                         :body   {:docs []}}

                                        "dictionary-db/sf:fenster"
                                        {:status 200
                                         :body   {:entries [{:lemma-id "lemma:das fenster:noun"
                                                             :lemma    "das Fenster"}]}}

                                        "dictionary-db/_all_docs"
                                        {:status 200
                                         :body   {:rows [{:id  "lemma:das fenster:noun"
                                                          :doc {:_id         "lemma:das fenster:noun"
                                                                :type        "dictionary-entry"
                                                                :value       "das Fenster"
                                                                :translation [{:lang  "ru"
                                                                               :value "окно"}]}}]}}

                                        (throw (ex-info "unexpected request" request))))]
        (is (= ["das Fenster"]
               (mapv :value (#'dictionary/lookup-dictionary-entries "Fenster")))))))
