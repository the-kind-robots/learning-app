(ns backend.examples-test
  (:require
   [backend.support.generation :as support.generation]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [db :as db]
   [dictionary.sqlite]
   [examples :as sut]
   [examples.dictionary :as dictionary]
   [examples.provider :as provider]))


(def ^:private good-example
  {:value       "Die Leiter steht neben der Wand."
   :translation "Лестница стоит рядом со стеной."
   :structure   [{:usedForm "Leiter" :dictionaryForm "die Leiter" :translation "лестница"}
                 {:usedForm "steht" :dictionaryForm "stehen" :translation "стоять"}
                 {:usedForm "Wand" :dictionaryForm "die Wand" :translation "стена"}]})


(def ^:private too-short-example
  (assoc good-example :value "Die Leiter."))


(defn- generating
  "Runs (f) with the provider answering each request with the next of
   `responses` (the last repeats) and the dictionary knowing nothing. The only
   things stood in for are the provider request and the dictionary lookup.
   Returns what (f) returns."
  [responses f]
  (with-redefs [sut/example-api-request (support.generation/provider-answering-in-turn (atom 0) responses)
                dictionary/lookup-dictionary-entries (constantly nil)
                dictionary/lemma-ids    (constantly #{})]
    (f)))


(defn- generate
  [word translation attempts]
  (sut/generate-one! (sut/subject {:word word :translation translation}) nil attempts []))


(deftest a-generation-that-fails-transiently-is-retried-to-a-valid-example
  (testing "a provider error, then an invalid example, then a valid one: the valid one is the answer"
    (is (= (#'sut/add-word-indexes good-example)
           (generating [{:status 500 :body "oops"}
                        (support.generation/example-completion too-short-example)
                        (support.generation/example-completion good-example)]
                       #(generate "Leiter" "лестница" 3)))))
  (testing "a dropped connection is just another failed attempt"
    (is (= (#'sut/add-word-indexes good-example)
           (generating [(ex-info "network down" {:status 0})
                        (support.generation/example-completion good-example)]
                       #(generate "Leiter" "лестница" 2)))))
  (testing "when every attempt fails the caller gets no example"
    (is (nil? (generating [(support.generation/example-completion too-short-example)]
                          #(generate "Leiter" "лестница" 3))))
    (is (sut/generation-failure?
         (generating [(ex-info "network down" {:status 0})] #(generate "Hund" "собака" 2)))
        "a provider that cannot be reached is its failure, not the pair's")))


(def ^:private other-example
  (assoc good-example
         :value       "Die Leiter lehnt an der Wand."
         :translation "Лестница прислонена к стене."))


(defn- generating-for
  "Like `generating`, with the prompt of every request kept in `prompts`."
  [prompts responses f]
  (let [answer (support.generation/provider-answering-in-turn (atom 0) responses)]
    (with-redefs [sut/example-api-request (fn [& args]
                                            (swap! prompts conj
                                              (#'sut/user-prompt
                                               (nth args 0)
                                               (nth args 1)
                                               (nth args 2)
                                               (nth args 3)
                                               nil
                                               (nth args 4)))
                                            (apply answer args))
                  dictionary/lookup-dictionary-entries (constantly nil)]
      (f))))


(deftest the-sentences-to-avoid-reach-the-prompt
  (let [prompts (atom [])]
    (generating-for prompts
                    [(support.generation/example-completion other-example)]
                    #(sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"})
                                        nil
                                        1
                                        ["Die Leiter steht neben der Wand."]))
    (is (str/includes? (first @prompts) "\"avoid\":[\"Die Leiter steht neben der Wand.\"]"))
    (is (not (str/includes?
              (#'sut/user-prompt "Leiter" ["лестница"] nil nil nil nil)
              "avoid"))
        "a request that avoids nothing says nothing about it")))


(deftest only-a-retry-is-asked-at-the-retry-temperature
  (let [temperature #(:temperature (#'sut/request-body "Leiter" ["лестница"] nil nil %))]
    (is (= @#'sut/first-attempt-temperature (temperature {:avoid ["Die Leiter steht neben der Wand."]}))
        "the first attempt avoiding sentences is still a first attempt")
    (is (= @#'sut/retry-temperature
           (temperature {:avoid   ["Die Leiter steht neben der Wand."]
                         :example good-example
                         :issue   :avoided-sentence})))))


(deftest an-avoided-sentence-is-rejected-and-the-generation-retried
  (testing "the same sentence, spelled with other case and spacing, is not the answer"
    (let [prompts (atom [])]
      (is (= (#'sut/add-word-indexes other-example)
             (generating-for prompts
                             [(support.generation/example-completion good-example)
                              (support.generation/example-completion other-example)]
                             #(sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"})
                                                 nil
                                                 3
                                                 [" die leiter steht neben der wand. "]))))
      (is (= 2 (count @prompts)))
      (is (str/includes? (second @prompts) "avoided-sentence")
          "the retry names what was wrong")
      (is (str/includes? (second @prompts) "\"avoid\":[")
          "and still lists what to avoid")))
  (testing "punctuation does not make a sentence another one"
    (are [avoided] (= (#'sut/add-word-indexes other-example)
                      (generating [(support.generation/example-completion good-example)
                                   (support.generation/example-completion other-example)]
                                  #(sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"})
                                                      nil
                                                      3
                                                      [avoided])))
     "Die Leiter steht neben der Wand!"
     "die leiter steht neben der wand"
     "Die Leiter, steht neben der Wand."))
  (testing "when every attempt repeats it, the caller gets no example"
    (is (nil? (generating [(support.generation/example-completion good-example)]
                          #(sut/generate-one! (sut/subject {:word "Leiter" :translation "лестница"})
                                              nil
                                              2
                                              [(:value good-example)]))))))


(deftest a-rate-limited-generation-is-not-retried
  (testing "the failure is reported at once, though a valid answer waited behind it"
    (let [result (generating [{:status 429 :body "{\"error\":{\"code\":\"rate_limit_exceeded\"}}"}
                              (support.generation/example-completion good-example)]
                             #(generate "Leiter" "лестница" 3))]
      (is (sut/generation-failure? result))
      (is (= 429 (:status result))))))


(deftest a-meta-answer-in-the-wrong-language-is-not-an-example
  (is (nil? (generating [(support.generation/example-completion
                          {:value       "The example for 'aufstehen' is ..."
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
  (are [flaw example] (= :malformed-example (:issue (#'sut/example-issue "Hund" {} nil #{} example)))
   "English mixed into the translation"
    (assoc dog :translation "The dog runs quickly in the park, собака.")

   "a small Latin tail in the translation"
    (assoc dog :translation "Собака бежит fast.")

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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "Leiter" {} nil #{} example))))))))


(deftest structure-items-out-of-sentence-order-are-placed-in-sentence-order
  (testing "an item out of order still finds its word, and the structure comes back in sentence order"
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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (nil? (:issue (#'sut/example-issue "aufpassen" {} nil #{} example))))
        (is (= [0 1 3]
               (mapv :wordIndex (:structure (#'sut/add-word-indexes example)))))))))


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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is
         (nil? (:issue (#'sut/example-issue "aufpassen" {} nil #{} example)))
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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (nil? (#'sut/example-issue "auf jeden Fall" {} nil #{} example))
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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (nil? (#'sut/example-issue "den Kopf verlieren" {} nil #{} example)))))))


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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (nil? (#'sut/example-issue "von Zeit zu Zeit" {} nil #{} example)))
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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "von Zeit zu Zeit" {} nil #{} example))))))))


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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (= :target-lemma-missing
               (:issue (#'sut/example-issue "auf jeden Fall" {} nil #{} example)))
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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (nil? (#'sut/example-issue "das heißt" {} nil #{} example)))))))


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
      (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                    dictionary/lemma-ids (constantly #{})]
        (is (= nil
               (#'sut/example-issue
                "vorstellen"
                {}
                nil
                #{}
                example)))))))


(deftest a-subject-reads-what-the-caller-sent
  (testing "one gloss as a bare string"
    (is (= ["собака"] (:translations (sut/subject {:word "Hund" :translation "собака"})))))
  (testing "several as a collection, sorted — their order is this device's, not anyone's"
    (is (= ["пёс" "собака"]
           (:translations (sut/subject {:word "Hund" :translation ["собака" "пёс"]})))))
  (testing "none at all"
    (is (= [] (:translations (sut/subject {:word "Hund"})))))
  (testing "blanks and duplicates are not glosses"
    (is (= ["собака"]
           (:translations (sut/subject {:word "Hund" :translation [" собака " "   " "собака"]})))))
  (testing "the word keeps its case and loses its spacing"
    (is (= "Hund" (:word (sut/subject {:word " Hund "})))))
  (testing "a blank context is no context"
    (is (nil? (:context (sut/subject {:word "Hund" :context "   "}))))
    (is (= "Tiere" (:context (sut/subject {:word "Hund" :context " Tiere "}))))))


(def ^:private dictionary-lemmas
  [{:value        "das Fenster"
    :pos          "noun"
    :rank         900
    :forms        ["Fensters" "Fenstern"]
    :translations [{:lang "ru" :value "окно"}]
    :enrichment   {:cefr-level "a1"}}
   {:value        "der Ärger"
    :pos          "noun"
    :rank         500
    :forms        []
    :translations [{:lang "ru" :value "досада"}]}
   {:value        "die Leiter"
    :pos          "noun"
    :rank         400
    :forms        []
    :translations [{:lang "ru" :value "лестница"}]}
   {:value        "der Leiter"
    :pos          "noun"
    :rank         300
    :forms        []
    :translations [{:lang "ru" :value "руководитель"}]}
   {:value        "gutern"
    :pos          "verb"
    :rank         990
    :forms        ["gute"]
    :translations [{:lang "ru" :value "ничего"}]}
   {:value        "das Gute"
    :pos          "noun"
    :rank         10
    :forms        []
    :translations [{:lang "ru" :value "добро"}]}
   {:value        "Bezug auf etwas nehmen"
    :pos          "phrase"
    :rank         100
    :forms        []
    :translations [{:lang "ru" :value "ссылаться"}]}
   {:value        "schon"
    :pos          "adv"
    :rank         200
    :forms        []
    :translations [{:lang "ru" :value "уже"}]}
   {:value        "schön"
    :pos          "adj"
    :rank         150
    :forms        []
    :translations [{:lang "ru" :value "красивый"}]}
   {:value        "explodieren"
    :pos          "verb"
    :rank         710
    :forms        ["explodiert" "explodierte"]
    :translations [{:lang "ru" :value "взрываться"}]}
   {:value        "explodiert"
    :pos          "adj"
    :rank         309
    :forms        []
    :translations [{:lang "ru" :value "взорванный"}]}
   {:value        "federn"
    :pos          "verb"
    :rank         120
    :forms        ["federt"]
    :translations [{:lang "ru" :value "пружинить"}]}
   {:value        "die Feder"
    :pos          "noun"
    :rank         650
    :forms        ["Federn"]
    :translations [{:lang "ru" :value "перо"}]}
   {:value        "erhöhen"
    :pos          "verb"
    :rank         800
    :forms        ["erhöht" "erhöhte"]
    :translations [{:lang "ru" :value "повышать"}]}
   {:value        "erhöht"
    :pos          "adj"
    :rank         250
    :forms        []
    :translations [{:lang "ru" :value "повышенный"}]}
   {:value        "nehmen"
    :pos          "verb"
    :rank         950
    :forms        ["nahm" "nimmt"]
    :translations [{:lang "ru" :value "брать"}]}
   {:value        "der Rauch"
    :pos          "noun"
    :rank         600
    :forms        []
    :translations [{:lang "ru" :value "дым"}]}
   {:value        "rauch"
    :pos          "adj"
    :rank         300
    :forms        []
    :translations [{:lang "ru" :value "густой"} {:lang "ru" :value "волосатый"}]}])


(defn- temp-dir
  []
  (.toFile (java.nio.file.Files/createTempDirectory
            "dictionary-test"
            (make-array java.nio.file.attribute.FileAttribute 0))))


(defn- with-dictionary
  "Runs (f) with lookups reading a dictionary built by the real build code, then
   closes it, so the next lookup opens the dictionary afresh."
  [f]
  (let [lookups @#'dictionary/connection
        dir     (temp-dir)]
    (try
      (dictionary.sqlite/write-dictionary! (str dir) dictionary-lemmas)
      (dictionary/open! {"LEARNING_APP__DICTIONARY_DIR" (str dir)})
      (f)
      (finally
       (when-let [opened @lookups] (.close ^java.sql.Connection opened))
       (reset! lookups nil)
       (run! io/delete-file (reverse (file-seq dir)))))))


(deftest a-bare-word-resolves-through-its-surface-form-to-the-lemma-entry
  (with-dictionary
   (fn []
     (let [entry (first (dictionary/lookup-dictionary-entries "Fenster"))]
       (is (= "das Fenster" (:value entry)))
       (is (= "noun" (:pos entry)))
       (is (= "a1" (get-in entry [:meta :cefr_level])))
       (is (= [{:lang "ru" :value "окно"}] (:translation entry)))))))


(deftest a-phrase-without-an-article-is-found-as-any-word
  (with-dictionary
   (fn []
     (is (= ["Bezug auf etwas nehmen"]
            (mapv :value (dictionary/lookup-dictionary-entries "Bezug auf etwas nehmen")))))))


(deftest an-article-keeps-the-lemma-distinct
  (with-dictionary
   (fn []
     (are [word values] (= values (mapv :value (dictionary/lookup-dictionary-entries word)))
      "die Leiter" ["die Leiter"]
      "der Leiter" ["der Leiter"]
      "Leiter"     ["die Leiter" "der Leiter"]))))


(deftest a-bare-word-is-found-exact-first-whatever-the-rank
  (with-dictionary
   (fn []
     (is (= ["das Gute" "gutern"]
            (mapv :value (dictionary/lookup-dictionary-entries "Gute")))
         "the noun written exactly so outranks a verb that merely has the form"))))


(deftest a-word-with-an-umlaut-is-found
  (with-dictionary
   (fn []
     (is (= ["der Ärger"] (mapv :value (dictionary/lookup-dictionary-entries "Ärger")))))))


(deftest a-word-typed-without-its-umlaut-also-finds-the-umlaut-twin
  (with-dictionary
   (fn []
     (is (= #{"schon" "schön"}
            (set (map :value (dictionary/lookup-dictionary-entries "schon")))))
     (is (= "adv" (:partOfSpeech (dictionary/lookup-word-meta "schon" ["уже"] nil)))
         "the gloss picks the word typed, not its twin")
     (is (= "adj" (:partOfSpeech (dictionary/lookup-word-meta "schon" ["красивый"] nil)))))))


(deftest a-picked-part-of-speech-decides-the-entry
  (with-dictionary
   (fn []
     (are [translations pos lemma] (= lemma (:lemma (dictionary/lookup-word-meta "rauch" translations pos)))
      []         nil    "der Rauch"
      ["густой"] nil    "rauch"
      []         "adj"  "rauch"
      ["дым"]    "adj"  "rauch"
      []         "noun" "der Rauch"
      ["густой"] "noun" "der Rauch"
      []         "verb" "der Rauch"))))


(deftest the-adjective-and-the-noun-do-not-share-a-cached-example
  (with-dictionary
   (fn []
     (let [key-of (fn [pos]
                    (let [subject (sut/subject {:word "rauch" :pos pos})]
                      (sut/subject-key subject (sut/word-meta subject))))]
       (is (not= (key-of "adj") (key-of nil)))
       (is (= (key-of "noun") (key-of nil))
           "the noun is what the lookup chooses without a pick, so it shares that row")))))


(deftest a-picked-part-of-speech-rejects-the-word-of-another-one
  (with-dictionary
   (fn []
     (let [example (fn [sentence used form]
                     {:value       sentence
                      :translation "Дым фабрики густой."
                      :structure   [{:usedForm used :dictionaryForm form :translation "дым"}]})
           noun    (example "Der Rauch der Fabrik ist dicht." "Rauch" "der Rauch")
           adj     (example "Ihre Haare sind sehr rauch." "rauch" "rauch")]
       (are [pos example issue]
        (= issue (:issue (#'sut/example-issue "rauch" (sut/target-lemma-ids "rauch") pos #{} example)))
        "adj" noun :wrong-part-of-speech
        "adj" adj  nil
        nil   noun nil))
     (let [participle
           {:value       "Die explodierte Bombe war laut."
            :translation "Взорванная бомба была громкой."
            :structure   [{:usedForm "explodierte" :dictionaryForm "explodieren" :translation "взрываться"}]}]
       (are [target pos issue]
        (= issue (:issue (#'sut/example-issue target (sut/target-lemma-ids target) pos #{} participle)))
        "explodiert" "adj"  nil
        "explodiert" "verb" nil
        "explodiert" "noun" :wrong-part-of-speech)))))


(deftest a-subject-keeps-the-picked-part-of-speech
  (are [pos expected] (= expected (:pos (sut/subject {:word "rauch" :pos pos})))
   nil     nil
   "  "    nil
   " adj " "adj"))


(deftest a-word-the-dictionary-does-not-know-has-no-entry
  (with-dictionary
   (fn []
     (is (= [] (dictionary/lookup-dictionary-entries "Quatsch")))
     (is (nil? (dictionary/lookup-word-meta "Quatsch" ["чушь"] nil))))))


(deftest a-missing-dictionary-file-stops-the-start-naming-the-file
  (let [dir   (.toString (java.nio.file.Files/createTempDirectory
                          "dictionary-missing"
                          (make-array java.nio.file.attribute.FileAttribute 0)))
        error (try (dictionary/open! {"LEARNING_APP__DICTIONARY_DIR" dir})
                   (catch clojure.lang.ExceptionInfo error error))]
    (is (re-find #"dictionary\.sqlite" (ex-message error)))))


(deftest the-provider-retry-after-reads-seconds-and-dates
  (let [now (java.time.Instant/parse "1994-11-06T08:49:00Z")
        retry-after (fn [value] (provider/retry-after-ms {:headers {:retry-after value}} now))]
    (are [header delay-ms] (= delay-ms (retry-after header))
     "7" 7000
     "0" 0
     "Sun, 06 Nov 1994 08:49:37 GMT" 37000
     "Sunday, 06-Nov-94 08:49:37 GMT" 37000
     "Sun Nov  6 08:49:37 1994" 37000
     "Sun, 06 Nov 1994 08:00:00 GMT" 0
     "86400" 3600000
     "soon" nil
     "-5" nil)))


(deftest a-dictionary-that-cannot-be-read-is-a-failure-not-an-unknown-word
  (with-redefs [dictionary/lookup-dictionary-entries (fn [_form] (throw (ex-info "Dictionary file unreadable" {})))]
    (is (sut/generation-failure? (sut/word-meta (sut/subject {:word "Fenster" :translation "окно"}))))))


(defn- candidate
  "An example of `value` whose `structure` is the `[used dictionary gloss]`
   triples."
  [value translation & items]
  {:value       value
   :translation translation
   :structure   (mapv (fn [[used-form dictionary-form gloss]]
                        {:usedForm used-form :dictionaryForm dictionary-form :translation gloss})
                      items)})


(def ^:private rejected-before
  "Candidates a model wrote that the validator once rejected, each with the
   target it was written for."
  {:placeholder-filled
   ["Anspruch auf etwas haben"
    (candidate "Jeder Arbeitnehmer hat einen Anspruch auf Urlaub."
               "Каждый работник имеет право на отпуск."
               ["Arbeitnehmer" "der Arbeitnehmer" "работник"]
               ["hat" "haben" "иметь"]
               ["Anspruch" "der Anspruch" "право"]
               ["Urlaub" "der Urlaub" "отпуск"])]

   :reflexive-with-a-noun-slot
   ["sich vor etwas drücken"
    (candidate "Er versucht, sich vor der Verantwortung zu drücken."
               "Он пытается уклониться от ответственности."
               ["versucht" "versuchen" "пытаться"]
               ["sich" "sich drücken" "уклоняться"]
               ["Verantwortung" "die Verantwortung" "ответственность"]
               ["drücken" "sich drücken" "уклоняться"])]

   :dative-slot-and-the-verb-named-in-the-expression
   ["jemandem Mut machen"
    (candidate "Der Trainer versuchte, seinem Team Mut zu machen."
               "Тренер пытался придать своей команде смелости."
               ["Trainer" "der Trainer" "тренер"]
               ["versuchte" "versuchen" "пытаться"]
               ["Team" "das Team" "команда"]
               ["Mut" "der Mut" "смелость"]
               ["machen" "jemandem Mut machen" "придать кому-то смелости"])]

   :another-article-than-the-targets
   ["ein Versprechen brechen"
    (candidate "Ich werde mein Versprechen nicht brechen."
               "Я не нарушу свое обещание."
               ["Versprechen" "das Versprechen" "обещание"]
               ["brechen" "brechen" "нарушить"])]

   :expression-named-whole
   ["in einer Krise stecken"
    (candidate "Die Firma steckt seit Monaten in einer Krise."
               "Фирма переживает кризис уже несколько месяцев."
               ["steckt" "in einer Krise stecken" "переживать кризис"]
               ["Monaten" "der Monat" "месяц"]
               ["einer" "ein" "один"]
               ["Krise" "die Krise" "кризис"])]

   :adjective-inflected-differently-from-the-target
   ["der gemeinsame Nenner"
    (candidate "Wir müssen einen gemeinsamen Nenner finden, um die Diskussion zu beenden."
               "Нам нужно найти общий знаменатель, чтобы закончить дискуссию."
               ["gemeinsamen" "gemeinsam" "общий"]
               ["Nenner" "der Nenner" "знаменатель"]
               ["finden" "finden" "находить"]
               ["müssen" "müssen" "быть должным"]
               ["Diskussion" "die Diskussion" "дискуссия"]
               ["beenden" "beenden" "заканчивать"])]

   :plural-listed-in-the-singular
   ["Aufzeichnungen"
    (candidate "Die Aufzeichnungen des Arztes waren sehr detailliert."
               "Записи врача были очень подробными."
               ["Aufzeichnungen" "die Aufzeichnung" "запись"]
               ["Arztes" "der Arzt" "врач"]
               ["waren" "sein" "быть"]
               ["detailliert" "detailliert" "подробный"])]

   :participle-said-as-a-verb
   ["verzweifelt"
    (candidate "Er verzweifelt an der Aufgabe."
               "Он отчаивается из-за задания."
               ["verzweifelt" "verzweifeln" "отчаиваться"]
               ["Aufgabe" "die Aufgabe" "задание"])]

   :separable-verb-with-items-out-of-order
   ["abschaffen"
    (candidate "Die Regierung will die alten Gesetze abschaffen."
               "Правительство хочет отменить старые законы."
               ["Regierung" "die Regierung" "правительство"]
               ["will" "wollen" "хотеть"]
               ["alte" "alt" "старый"]
               ["Gesetze" "das Gesetz" "закон"]
               ["abschaffen" "abschaffen" "упразднять"])]

   :detached-prefix-after-its-sentence-neighbours
   ["hervorgehen"
    (candidate "Aus den Daten geht die Korrelation hervor."
               "Из данных вытекает корреляция."
               ["geht" "hervorgehen" "вытекать"]
               ["Daten" "die Daten" "данные"]
               ["Korrelation" "die Korrelation" "корреляция"]
               ["hervor" "hervorgehen" "вытекать"])]

   :two-words-in-one-item
   ["der Genius Loci"
    (candidate "Der Genius Loci prägt die Atmosphäre dieses Ortes."
               "Гений места формирует атмосферу этого места."
               ["Genius Loci" "der Genius Loci" "гений места"]
               ["prägt" "prägen" "формировать"]
               ["Atmosphäre" "die Atmosphäre" "атмосфера"]
               ["Ortes" "der Ort" "место"])]

   :lemma-where-the-sentence-has-an-inflected-form
   ["der Widerstand"
    (candidate "Der Widerstand gegen die neue Regel war groß."
               "Сопротивление новому правилу было большим."
               ["Widerstand" "der Widerstand" "сопротивление"]
               ["gegen" "gegen" "против"]
               ["neu" "neu" "новый"]
               ["Regel" "die Regel" "правило"]
               ["war" "sein" "быть"]
               ["groß" "groß" "большой"])]

   :item-glossed-in-english
   ["annehmen"
    (candidate "Ich nehme an, dass er die Wahrheit sagt."
               "Я предполагаю, что он говорит правду."
               ["nehme" "annehmen" "предполагать"]
               ["dass" "dass" "что"]
               ["er" "er" "он"]
               ["die" "die" "the"]
               ["Wahrheit" "die Wahrheit" "правда"]
               ["sagt" "sagen" "говорить"])]

   :time-in-the-translation
   ["die Tagesschau"
    (candidate "Die Tagesschau beginnt um 20 Uhr."
               "Ежедневный выпуск новостей начинается в 20:00."
               ["Tagesschau" "die Tagesschau" "ежедневный выпуск новостей"]
               ["beginnt" "beginnen" "начинается"]
               ["Uhr" "die Uhr" "час"])]

   :sentence-longer-than-a-handful-around-the-target
   ["jemanden an der Backe haben"
    (candidate "Ich habe ihn wirklich an der Backe, aber ich muss mit ihm arbeiten."
               "Я действительно вынужден иметь с ним дело, но мне приходится с ним работать."
               ["habe" "haben" "иметь"]
               ["ihn" "er" "он"]
               ["Backe" "die Backe" "щека"]
               ["muss" "müssen" "быть вынужденным"]
               ["ihm" "er" "он"]
               ["arbeiten" "arbeiten" "работать"])]})


(deftest a-correct-sentence-the-validator-once-rejected-is-accepted
  (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                dictionary/lemma-ids (constantly #{})]
    (doseq [[reason [target example]] rejected-before]
      (testing (name reason)
        (is (nil? (#'sut/example-issue target {} nil #{} example)))))))


(deftest a-placeholder-is-a-slot-the-sentence-fills-however-it-likes
  (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                dictionary/lemma-ids (constantly #{})]
    (let [fixed     (candidate "Wir legen den Termin bald fest."
                               "Мы скоро назначим срок."
                               ["legen" "festlegen" "назначать"]
                               ["Termin" "der Termin" "срок"]
                               ["bald" "bald" "скоро"]
                               ["fest" "festlegen" "назначать"])
          reflexive (fn [pronoun]
                      (candidate (str "Ich stelle " pronoun " morgen bei den Kollegen vor.")
                                 "Я представляюсь коллегам завтра."
                                 ["stelle" "sich vorstellen" "представляться"]
                                 ["Kollegen" "der Kollege" "коллега"]
                                 ["vor" "sich vorstellen" "представляться"]))]
      (are [target example] (nil? (#'sut/example-issue target {} nil #{} example))
       "etwas festlegen"         fixed
       "etw. festlegen"          fixed
       "sich vorstellen"         (reflexive "mich")
       "sich bei jdm vorstellen" (reflexive "mich")
       "sich bei jemandem vorstellen" (reflexive "uns")))))


(deftest a-word-of-the-phrase-that-is-no-placeholder-is-still-required
  (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                dictionary/lemma-ids (constantly #{})]
    (let [example (candidate "Jeder Arbeitnehmer hat einen Anspruch auf Urlaub."
                             "Каждый работник имеет право на отпуск."
                             ["Arbeitnehmer" "der Arbeitnehmer" "работник"]
                             ["hat" "haben" "иметь"]
                             ["Anspruch" "der Anspruch" "право"]
                             ["Urlaub" "der Urlaub" "отпуск"])]
      (are [target] (= :target-lemma-missing (:issue (#'sut/example-issue target {} nil #{} example)))
       "Anspruch auf etwas verlieren"
       "Bezug auf etwas nehmen"
       "etwas zur Folge haben"))))


(deftest a-structure-item-that-fits-no-word-costs-its-tooltip-not-the-example
  (let [example (candidate "Der Widerstand gegen die neue Regel war groß."
                           "Сопротивление новому правилу было большим."
                           ["Widerstand" "der Widerstand" "сопротивление"]
                           ["neu" "neu" "новый"]
                           ["Regel" "die Regel" "правило"]
                           ["Gericht" "das Gericht" "суд"])]
    (is (= [["Widerstand" 1] ["Regel" 5]]
           (mapv (juxt :usedForm :wordIndex)
                 (:structure (#'sut/add-word-indexes example)))))))


(deftest an-item-of-two-words-is-indexed-word-by-word
  (let [example (candidate "Der Genius Loci prägt die Atmosphäre dieses Ortes."
                           "Гений места формирует атмосферу этого места."
                           ["Genius Loci" "der Genius Loci" "гений места"]
                           ["prägt" "prägen" "формировать"])]
    (is (= [["Genius" 1] ["Loci" 2] ["prägt" 3]]
           (mapv (juxt :usedForm :wordIndex)
                 (:structure (#'sut/add-word-indexes example)))))))


(deftest a-structure-with-no-word-of-the-sentence-is-a-structure-mismatch
  (with-redefs [dictionary/lookup-dictionary-entries (constantly nil)
                dictionary/lemma-ids (constantly #{})]
    (is (= :structure-mismatch
           (:issue (#'sut/example-issue
                    "Leiter"
                    {}
                    nil
                    #{}
                    (candidate "Der Hund läuft schnell im Park."
                               "Собака быстро бежит по парку."
                               ["Leiter" "die Leiter" "лестница"])))))))


(deftest a-form-belongs-to-every-lemma-it-is-a-form-of
  (with-dictionary
   (fn []
     (are [form n] (= n (count (dictionary/lemma-ids form)))
      "explodiert"   2
      "Federn"       2
      "erhöht"       2
      "sich erhöhen" 1
      "die Feder"    1
      "die Federn"   0
      "Quatsch"      0)
     (is (= (dictionary/lemma-ids "erhöhen") (dictionary/lemma-ids "sich erhöhen"))
         "`sich` is not part of the lemma")
     (is (not= (dictionary/lemma-ids "die Feder") (dictionary/lemma-ids "Federn"))
         "an article names its own lemma only"))))


(defn- issue-of
  "The issue the validator finds in the sentence `value` for `target`, with
   the dictionary read as it is for a generation. `items` are the structure
   triples."
  [target value & items]
  (:issue (#'sut/example-issue
           target
           (sut/target-lemma-ids target)
           nil
           #{}
           (apply candidate value "Перевод предложения." items))))


(deftest a-word-saved-in-one-form-is-present-in-another-form-of-its-lemma
  (with-dictionary
   (fn []
     (are [target value items] (nil? (apply issue-of target value items))
      "explodiert" "Die Bombe explodierte laut im Hof."
                                                        [["Bombe" "die Bombe" "бомба"]
                                                         ["Hof" "der Hof" "двор"]]
      "federn"     "Die Feder liegt auf dem Tisch."
                                                        [["Feder" "die Feder" "перо"]
                                                         ["liegt" "liegen" "лежать"]]
      "federn"     "Die Federn des Vogels sind weich."
                                                        [["Vogels" "der Vogel" "птица"]
                                                         ["weich" "weich" "мягкий"]]
      "erhöht"     "Der Chef erhöhte den Preis stark."
                                                        [["Chef" "der Chef" "начальник"]
                                                         ["Preis" "der Preis" "цена"]]
      "Bezug auf etwas nehmen" "Er nahm auf die Regel Bezug." [["Regel" "die Regel" "правило"]]
      "Quatsch"    "Das ist doch alles Quatsch hier."   [["alles" "alles" "всё"]]))))


(deftest a-word-is-missing-when-the-sentence-has-no-form-of-its-lemma
  (with-dictionary
   (fn []
     (are [target value items] (= :target-lemma-missing (apply issue-of target value items))
      "explodiert" "Der Hund läuft schnell im Park."
                                                     [["Hund" "der Hund" "собака"]
                                                      ["Park" "der Park" "парк"]]
      "erhöht"     "Der Hund läuft schnell im Park." [["Hund" "der Hund" "собака"]]
      "Quatsch"    "Das ist doch alles Unsinn hier." [["alles" "alles" "всё"]]
      "explodiert" "Der Hund läuft schnell im Park."
                                                     [["Hund" "der Hund" "собака"]
                                                      ["explodierte" "explodieren" "взрываться"]]
      "Bezug auf etwas nehmen" "Er nahm auf die Regel Rücksicht." [["Regel" "die Regel" "правило"]]))))
