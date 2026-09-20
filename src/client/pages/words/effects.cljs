(ns pages.words.effects
  (:require
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [pages.words.presenter :as presenter]
   [use-cases.vocabulary :as vocabulary]))


(defn- ^:async show!
  "Reads one page of the active scope and hands it to the view. The page is
   the first `limit` rows, not a window at an offset: the sort has already
   read every review, so a growing limit costs the same query and keeps the
   loaded row count a single number that survives a reload."
  [dispatch capabilities {:keys [limit search]}]
  (try
    (let [limit (or limit presenter/page-size)
          {:keys [matches total words]}
          (await (vocabulary/list-active capabilities {:order :asc :search search :limit limit}))]
      (dispatch [[:action/show-words
                  {:limit   limit
                   :matches matches
                   :search  search
                   :total   total
                   :words   words}]]))
    (catch js/Error err
      (log/error :effect/load-words {:error (str err)}))))


(def ^:private search!
  (gfn/debounce
   (fn search! [dispatch capabilities opts] (show! dispatch capabilities opts))
   400))


(defonce ^:private sentinel-observer (atom nil))


(defonce ^:private loading-more? (atom false))


(defn- disconnect-sentinel!
  []
  (when-let [observer @sentinel-observer]
    (.disconnect observer)
    (reset! sentinel-observer nil)))


(nxr/register-action! :action/go-to-words
  (fn go-to-words [_]
    [[:effect/navigate :page/words]]))


(nxr/register-effect! :effect/load-words
  (fn load-words
    [{:keys [capabilities dispatch]} _ opts]
    (show! dispatch capabilities opts)))


(nxr/register-effect! :effect/set-words-search
  (fn set-words-search
    [{:keys [capabilities dispatch]} _ opts]
    (search! dispatch capabilities opts)))


(nxr/register-effect! :effect/scroll-words-to-top
  (fn scroll-words-to-top [_ _]
    (when-let [node (js/document.querySelector ".vocabulary__list")]
      (set! (.-scrollTop node) 0))))


(nxr/register-effect! :effect/load-more-words
  ;; The observer can fire again before the page it asked for has arrived —
  ;; a re-render that replaces the sentinel node is enough. The flag makes
  ;; the second call a no-op instead of a second query.
  (fn ^:async load-more-words
    [{:keys [capabilities dispatch]} _ opts]
    (when-not @loading-more?
      (reset! loading-more? true)
      (try
        (await (show! dispatch capabilities opts))
        (finally
         (reset! loading-more? false))))))


(nxr/register-effect! :effect/observe-words-sentinel
  ;; The sentinel is keyed, so appending rows in front of it reuses the node
  ;; and this runs once per visit to the screen. `rootMargin` asks for the
  ;; next page a screenful early, so the reader meets rows rather than a wait.
  (fn observe-words-sentinel
    [{:keys [dispatch dispatch-data]} _]
    (disconnect-sentinel!)
    (when-let [node (:replicant/node dispatch-data)]
      (let [observer (js/IntersectionObserver.
                      (fn [entries]
                        (when (some #(.-isIntersecting %) entries)
                          (dispatch [[:action/show-more-words]])))
                      #js {:rootMargin "200px"})]
        (.observe observer node)
        (reset! sentinel-observer observer)))))


(nxr/register-effect! :effect/unobserve-words-sentinel
  (fn unobserve-words-sentinel [_ _]
    (disconnect-sentinel!)))


(nxr/register-effect! :effect/update-word
  (fn ^:async update-word
    [{:keys [capabilities dispatch]} _ {:keys [id translation] :as opts}]
    (try
      (await (vocabulary/update! capabilities id translation))
      (await (show! dispatch capabilities opts))
      (catch js/Error err
        (log/error :effect/update-word {:error (str err)})))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word
    [{:keys [capabilities dispatch]} _ {:keys [id value] :as opts}]
    (let [collection-id ((:collections/active-id (:collections capabilities)))
          prompt        (if collection-id
                          (str "Убрать «" value "» из набора?")
                          (str "Удалить «" value "» окончательно?"))]
      (when (js/confirm prompt)
        (try
          (await (vocabulary/remove-from-active! capabilities id))
          (await (show! dispatch capabilities opts))
          (catch js/Error err
            (log/error :effect/delete-word {:error (str err)})))))))
