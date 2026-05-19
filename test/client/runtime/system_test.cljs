(ns client.runtime.system-test
  (:require-macros
   [client.support.test :refer [async-testing]])
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [runtime.system :as sut]))


(deftest resolve-layers-validates-missing-deps
  (testing "missing component keys fail before startup"
    (is (thrown-with-msg?
         js/Error
         #"Missing dependency definitions"
         (sut/resolve-dependencies {:app/a {:requires {:b :app/b}
                                            :start    identity}})))))


(deftest resolve-layers-detects-cycles
  (testing "cyclic components fail before startup"
    (is (thrown-with-msg?
         js/Error
         #"Dependency cycle detected"
         (sut/resolve-dependencies {:app/a {:requires {:b :app/b}
                                            :start    identity}
                                    :app/b {:requires {:a :app/a}
                                            :start    identity}})))))


(deftest compile-plan-uses-component-entries
  (let [components {:app/a {:start identity}
                    :app/b {:requires {:a :app/a}
                            :start    identity}}
        plan       (sut/compile-plan components)]
    (is (= [[:app/a] [:app/b]]
           (mapv #(mapv first %) plan)))
    (is (identical? (get components :app/a)
                    (second (first (first plan)))))))


(deftest start-system-passes-local-dependency-args
  (async-testing "start functions receive local-name -> value map"
    (let [seen (atom nil)
          res  (await (sut/start!
                       {:db/main {:start (fn [_] :db-value)}
                        :port/a  {:requires {:db :db/main}
                                  :start    (fn [deps]
                                              (reset! seen deps)
                                              :port-value)}}))]
      (is (= {:db :db-value} @seen))
      (is (= :port-value (get-in res [:values :port/a]))))))


(deftest start-system-starts-layer-in-parallel
  (async-testing "independent components in the same layer start before either finishes"
    (let [events    (atom [])
          release-a (atom nil)
          release-b (atom nil)
          started   (sut/start!
                     {:app/a {:start (fn [_]
                                       (swap! events conj :a-start)
                                       (js/Promise.
                                        (fn [resolve _reject]
                                          (reset! release-a resolve))))}
                      :app/b {:start (fn [_]
                                       (swap! events conj :b-start)
                                       (js/Promise.
                                        (fn [resolve _reject]
                                          (reset! release-b resolve))))}
                      :app/c {:requires {:a :app/a
                                         :b :app/b}
                              :start    (fn [{:keys [a b]}]
                                          (swap! events conj [:c-start a b])
                                          :c)}})]
      (await (js/Promise.resolve))
      (is (= [:a-start :b-start] @events))
      (@release-a :a)
      (@release-b :b)
      (let [res (await started)]
        (is (= [:a-start :b-start [:c-start :a :b]] @events))
        (is (= :c (get-in res [:values :app/c])))))))


(deftest start-system-stops-started-components-on-failure
  (async-testing "failed startup stops already-started components in reverse order"
    (let [events (atom [])]
      (try
        (await (sut/start!
                {:app/a {:start (fn [_]
                                  (swap! events conj :a-start)
                                  :a)
                         :stop  (fn [_]
                                  (swap! events conj :a-stop))}
                 :app/b {:requires {:a :app/a}
                         :start    (fn [_]
                                     (swap! events conj :b-start)
                                     :b)
                         :stop     (fn [_]
                                     (swap! events conj :b-stop))}
                 :app/c {:requires {:b :app/b}
                         :start    (fn [_]
                                     (swap! events conj :c-start)
                                     (throw (js/Error. "boom")))}}))
        (is false "startup should fail")
        (catch js/Error err
          (is (= "boom" (.-message err)))))
      (is (= [:a-start :b-start :c-start :b-stop :a-stop]
             @events)))))


(deftest stop-system-is-best-effort-reverse-order
  (async-testing "all stops run even if one throws"
    (let [events (atom [])]
      (await (sut/stop!
              [(fn [] (swap! events conj :a-stop))
               (fn []
                 (swap! events conj :b-stop)
                 (throw (js/Error. "ignore")))
               (fn [] (swap! events conj :c-stop))]))
      (is (= [:c-stop :b-stop :a-stop] @events)))))


(deftest component-graph-preserves-resource-order
  (let [components  {:app/store           {:start identity}
                     :worker/service-worker {:start identity}
                     :document/listeners  {:start identity}
                     :db/sqlite           {:start identity}
                     :db/pouch            {:start identity}
                     :port/clock          {:start identity}
                     :worker/task-runner  {:requires {:clock :port/clock
                                                      :db    :db/pouch}
                                           :start    identity}
                     :port/dictionary     {:requires {:db :db/sqlite}
                                           :start    identity}
                     :port/progress-store {:requires {:clock :port/clock
                                                      :db    :db/pouch}
                                           :start    identity}
                     :port/examples       {:requires {:clock :port/clock
                                                      :db    :db/pouch}
                                           :start    identity}
                     :port/navigation     {:start identity}
                     :app/capabilities    {:requires {:dictionary :port/dictionary
                                                      :examples   :port/examples
                                                      :navigation :port/navigation}
                                           :start    identity}
                     :nexus/system        {:requires {:capabilities :app/capabilities
                                                      :store        :app/store}
                                           :start    identity}
                     :app/router          {:after [:nexus/system
                                                   :worker/service-worker
                                                   :document/listeners]
                                           :start identity}}
        layers      (sut/resolve-dependencies components)
        layer-index (into {}
                          (mapcat (fn [[idx layer]]
                                    (map (fn [k] [k idx]) layer))
                           (map-indexed vector layers)))]
    (is (< (layer-index :db/pouch)
           (layer-index :port/progress-store)))
    (is (< (layer-index :db/pouch)
           (layer-index :worker/task-runner)))
    (is (< (layer-index :db/pouch)
           (layer-index :port/examples)
           (layer-index :app/capabilities)))
    (is (< (layer-index :db/sqlite)
           (layer-index :port/dictionary)
           (layer-index :app/capabilities)))
    (is (< (layer-index :nexus/system)
           (layer-index :app/router)))
    (is (< (layer-index :worker/service-worker)
           (layer-index :app/router)))
    (is (< (layer-index :document/listeners)
           (layer-index :app/router)))
    (is (= {:db :db/sqlite}
           (get-in components [:port/dictionary :requires])))))
