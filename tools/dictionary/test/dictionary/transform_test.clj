(ns dictionary.transform-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [dictionary.transform :as transform]))


;; Access private fns via var
(def ^:private compute-rank #'transform/compute-rank)


(def ^:private lemma-value #'transform/lemma-value)


(def ^:private bare-word #'transform/bare-word)


;; ---------------------------------------------------------------------------
;; compute-rank (private)
;; ---------------------------------------------------------------------------


(deftest compute-rank-a1-base-plus-bonus
  (testing "A1 base rank 30000 + sense/translation bonus"
    (is (= 30032 (compute-rank "a1" 3 2 nil)))))


(deftest compute-rank-a2-base-plus-bonus
  (testing "A2 base rank 20000 + bonus"
    (is (= 20021 (compute-rank "a2" 2 1 nil)))))


(deftest compute-rank-b1-base-plus-bonus
  (testing "B1 base rank 10000 + bonus"
    (is (= 10011 (compute-rank "b1" 1 1)))))


(deftest compute-rank-non-cefr-cap-at-5000
  (testing "non-CEFR entry capped at 5000"
    (is (= 5000 (compute-rank nil 100 100)))))


(deftest compute-rank-boundary-values
  (testing "zero counts give base rank only"
    (is (= 30000 (compute-rank "a1" 0 0)))))


(deftest compute-rank-zero-counts-non-cefr
  (testing "non-CEFR with zero counts gives 0"
    (is (= 0 (compute-rank nil 0 0)))))


(deftest compute-rank-b1-min-gt-non-cefr-max
  (testing "B1 minimum (10000) > non-CEFR maximum (5000)"
    (let [b1-min  (compute-rank "b1" 0 0)
          non-max (compute-rank nil 100 100)]
      (is (> b1-min non-max)))))


(deftest compute-rank-frequency-dominates-fallbacks
  (testing "frequency rank is the primary importance signal when present"
    (is (> (compute-rank nil 0 0 {:rank 10})
           (compute-rank "a1" 100 100)))))


;; ---------------------------------------------------------------------------
;; lemma-value (private)
;; ---------------------------------------------------------------------------


(deftest lemma-value-noun-with-canonical-value
  (testing "noun uses pre-computed :canonical-value from slim-entry"
    (is (= "der Hund"
           (lemma-value {:word "Hund" :canonical-value "der Hund"} "noun")))))


(deftest lemma-value-noun-no-canonical-value
  (testing "noun without :canonical-value falls back to bare word"
    (is (= "Tier"
           (lemma-value {:word "Tier"} "noun")))))


(deftest lemma-value-verb
  (testing "verb returns bare word regardless of :canonical-value"
    (is (= "gehen"
           (lemma-value {:word "gehen"} "verb")))))


(deftest lemma-value-verb-canonical-value-ignored
  (testing ":canonical-value is ignored for non-nouns"
    (is (= "laufen"
           (lemma-value {:word "laufen" :canonical-value "something"} "verb")))))


(deftest lemma-value-substantivized-adjective-uses-canonical-value
  (testing "substantivized adjective uses pre-computed :canonical-value"
    (is (= "der Angestellte"
           (lemma-value
            {:word "Angestellter" :canonical-value "der Angestellte"}
            "noun")))))


(deftest lemma-value-feminine-substantivized-adjective
  (testing "feminine substantivized adjective uses pre-computed :canonical-value"
    (is (= "die Angestellte"
           (lemma-value
            {:word "Angestellte" :canonical-value "die Angestellte"}
            "noun")))))


;; ---------------------------------------------------------------------------
;; bare-word (private)
;; ---------------------------------------------------------------------------


(deftest bare-word-der-stripping
  (testing "strips 'der' article"
    (is (= "Hund" (bare-word "der Hund")))))


(deftest bare-word-die-stripping
  (testing "strips 'die' article"
    (is (= "Katze" (bare-word "die Katze")))))


(deftest bare-word-das-stripping
  (testing "strips 'das' article"
    (is (= "Buch" (bare-word "das Buch")))))


(deftest bare-word-no-article
  (testing "no article returns word as-is"
    (is (= "gehen" (bare-word "gehen")))))


(deftest bare-word-partial-match-like-derzeit
  (testing "'derzeit' is not stripped (no space after 'der')"
    (is (= "derzeit" (bare-word "derzeit")))))


;; ---------------------------------------------------------------------------
;; lemma (public)
;; ---------------------------------------------------------------------------


(def ^:private sample-goethe-index
  [["hundefutter" "a1"] ["hund" "a1"] ["geh" "a2"] ["katz" "b1"]])


(deftest lemma-full-noun
  (testing "full noun entry produces correct text-id, value, pos, translations, forms, enrichment"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :canonical-value "der Hund"
                  :tags         ["masculine" "noun"]
                  :senses       [{:tags ["animal"] :glosses ["a dog"]}
                                 {:tags ["insult"] :glosses ["an unpleasant person"]}]
                  :translations [{:lang_code "ru" :word "собака"}
                                 {:lang_code "en" :word "dog"}]
                  :forms        [{:form "Hundes"} {:form "Hunde"} {:form "Hunden"}]}
          result (transform/lemma entry sample-goethe-index {})]
      (is (some? result))
      (is (= "der Hund" (:value result)))
      (is (= "noun" (:pos result)))
      (is (= "lemma:der hund:noun" (:text-id result)))
      (is (= [{:lang "ru" :value "собака"}] (:translations result)))
      (is (= ["Hundes" "Hunde" "Hunden"] (:forms result)))
      (is (= "a1" (get-in result [:enrichment :cefr-level])))
      (is (= [{:tags ["animal"] :glosses ["a dog"]}
              {:tags ["insult"] :glosses ["an unpleasant person"]}]
             (get-in result [:enrichment :senses])))
      (is (pos? (:rank result))))))


(deftest lemma-frequency-drives-rank
  (testing "frequency data drives rank via frequency-base-rank formula"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :canonical-value "der Hund"
                  :tags         ["masculine"]
                  :senses       [{:tags ["animal"]}]
                  :translations [{:lang_code "ru" :word "собака"}]
                  :forms        []}
          freq   {"hund" {:source "subtitles" :rank 7 :count 12345.0}}
          result (transform/lemma entry sample-goethe-index freq)]
      (is (= 999993 (:rank result))))))


(deftest lemma-frequency-uses-best-form-match
  (testing "rank uses the lemma's own frequency (frequency-candidates = value/bare/word)"
    (let [entry  {:word         "gehen"
                  :pos          "Verb"
                  :tags         []
                  :senses       [{:tags ["motion"]}]
                  :translations []
                  :forms        [{:form "ging"} {:form "gegangen"}]}
          freq   {"gehen"    {:source "subtlex-de" :rank 500 :count 100.0}
                  "ging"     {:source "subtlex-de" :rank 20 :count 1000.0}
                  "gegangen" {:source "subtlex-de" :rank 200 :count 300.0}}
          result (transform/lemma entry sample-goethe-index freq)]
      ;; candidates = ["gehen"] (value=bare=word for verb), matches rank 500
      (is (= 999500 (:rank result))))))


(deftest lemma-noun-frequency-matches-bare-form
  (testing "noun frequency lookup uses value and bare word (not inflected forms)"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :canonical-value "der Hund"
                  :tags         ["masculine"]
                  :senses       [{:tags ["animal"]}]
                  :translations []
                  :forms        [{:form "Hunde"} {:form "Hunden"}]}
          freq   {"hund"  {:source "subtlex-de" :rank 100 :count 100.0}
                  "hunde" {:source "subtlex-de" :rank 40 :count 300.0}}
          result (transform/lemma entry sample-goethe-index freq)]
      ;; frequency-candidates checks value/bare-word/word — "hund" matches at rank 100
      (is (= 999900 (:rank result))))))


(deftest lemma-verb
  (testing "verb entry uses bare word as value"
    (let [entry  {:word         "gehen"
                  :pos          "Verb"
                  :tags         []
                  :senses       [{:tags ["motion"]}]
                  :translations [{:lang_code "ru" :word "идти"}]
                  :forms        [{:form "ging"} {:form "gegangen"}]}
          result (transform/lemma entry sample-goethe-index {})]
      (is (some? result))
      (is (= "gehen" (:value result)))
      (is (= "verb" (:pos result))))))


(deftest lemma-empty-translations-when-no-russian
  (testing "entry with no Russian translations produces empty :translations"
    (let [entry  {:word         "Hund"
                  :pos          "Noun"
                  :canonical-value "der Hund"
                  :tags         ["masculine"]
                  :senses       [{:tags ["animal"]}]
                  :translations [{:lang_code "en" :word "dog"}]
                  :forms        []}
          result (transform/lemma entry sample-goethe-index {})]
      (is (some? result))
      (is (= [] (:translations result))))))


(deftest lemma-nil-for-disallowed-pos
  (testing "name POS is not in allowed-pos — returns nil"
    (let [entry {:word         "Berlin"
                 :pos          "Name"
                 :tags         []
                 :senses       [{:tags []}]
                 :translations [{:lang_code "ru" :word "Берлин"}]
                 :forms        []}]
      (is (nil? (transform/lemma entry [] {})))
      (is (nil? (transform/lemma entry [["berlin" "a1"]] {}))))))


(deftest lemma-text-id-format
  (testing "text-id uses lowercased value with pos suffix"
    (let [entry  {:word         "Größe"
                  :pos          "Noun"
                  :canonical-value "die Größe"
                  :tags         ["feminine"]
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "размер"}]
                  :forms        []}
          result (transform/lemma entry [] {})]
      (is (= "lemma:die größe:noun" (:text-id result))))))


(deftest lemma-pos-lowercasing
  (testing "POS is lowercased in output"
    (let [entry  {:word         "gehen"
                  :pos          "Verb"
                  :tags         []
                  :senses       [{:tags []}]
                  :translations [{:lang_code "ru" :word "идти"}]
                  :forms        []}
          result (transform/lemma entry [] {})]
      (is (= "verb" (:pos result))))))


(deftest lemma-nil-pos
  (testing "nil POS maps to unknown and is excluded"
    (let [entry {:word         "hmm"
                 :pos          nil
                 :tags         []
                 :senses       [{:tags []}]
                 :translations [{:lang_code "ru" :word "хмм"}]
                 :forms        []}]
      (is (nil? (transform/lemma entry [] {}))))))
