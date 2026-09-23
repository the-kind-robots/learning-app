(ns pages.words.effects
  (:require
   [goog.functions :as gfn]
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [pages.words.presenter :as presenter]
   [use-cases.vocabulary :as vocabulary]))


(defn- ^:async show!
  "Reads one page of the active scope and hands it to the view, stamped with
   the number of the read that asked for it — `:action/show-words` drops rows
   whose read a later one has overtaken. The page is the first `limit` rows,
   not a window at an offset: the loaded row count stays one number that
   survives a reload, and the rows it re-reads are the ones already on screen
   rather than the vocabulary."
  [dispatch capabilities {:keys [limit search]} token]
  (try
    (let [limit (or limit presenter/page-size)
          {:keys [matches total words]}
          (await (vocabulary/list-active capabilities
                                         {:order :alphabetical :search search :limit limit}))]
      (dispatch [[:action/show-words
                  {:limit   limit
                   :matches matches
                   :search  search
                   :token   token
                   :total   total
                   :words   words}]]))
    (catch js/Error err
      (log/error :effect/load-words {:error (str err)}))))


(def ^:private search!
  (gfn/debounce
   (fn search! [dispatch capabilities opts token]
     (.finally (show! dispatch capabilities opts token)
               (fn [] (dispatch [[:action/words-search-settled token]]))))
   400))


;; The sentinel observer and the node it watches, kept together. Replicant
;; appends unmount hooks after mount hooks (`get-hooks-to-call`), so on a
;; subtree swap the new observer is registered before the old node's unmount
;; runs — a handle holding the observer alone would have that unmount
;; disconnect the new observer, and infinite scroll would die silently for the
;; rest of the visit (#439). The `:replicant/key` on the sentinel is what keeps
;; this rare; it is not what makes it safe.
(defonce ^:private sentinel-watch (atom nil))


(defonce ^:private loading-more? (atom false))


(defn- disconnect-sentinel!
  "Stops the observer watching `node`. A nil node means whichever observer is
   there — the mount path, which is about to put its own in place."
  [node]
  (let [{:keys [observer] watched :node} @sentinel-watch]
    (when (and observer (or (nil? node) (= node watched)))
      (.disconnect observer)
      (reset! sentinel-watch nil))))


(nxr/register-action! :action/go-to-words
  (fn go-to-words [_]
    [[:effect/navigate :page/words]]))


(nxr/register-effect! :effect/load-words
  (fn load-words
    [{:keys [capabilities dispatch]} _ opts token]
    (show! dispatch capabilities opts token)))


(nxr/register-effect! :effect/set-words-search
  (fn set-words-search
    [{:keys [capabilities dispatch]} _ opts token]
    (search! dispatch capabilities opts token)))


(nxr/register-effect! :effect/scroll-words-to-top
  (fn scroll-words-to-top [_ _]
    (when-let [node (js/document.querySelector ".vocabulary__list")]
      (set! (.-scrollTop node) 0))))


(nxr/register-effect! :effect/load-more-words
  ;; The observer can fire again before the page it asked for has arrived —
  ;; a re-render that replaces the sentinel node is enough. The flag makes
  ;; the second call a no-op instead of a second query.
  (fn ^:async load-more-words
    [{:keys [capabilities dispatch]} _ opts token]
    (when-not @loading-more?
      (reset! loading-more? true)
      (try
        (await (show! dispatch capabilities opts token))
        (finally
         (reset! loading-more? false))))))


(nxr/register-effect! :effect/observe-words-sentinel
  ;; The sentinel is keyed, so appending rows in front of it reuses the node
  ;; and this runs once per visit to the screen. The root is the list — the
  ;; box that actually clips the sentinel. Left to its default the root is the
  ;; viewport, which never clips it, so `rootMargin` widened a rectangle that
  ;; decided nothing and the page was asked for only once the reader had
  ;; scrolled the sentinel into view (#439).
  (fn observe-words-sentinel
    [{:keys [dispatch dispatch-data]} _]
    (when-let [node (:replicant/node dispatch-data)]
      (disconnect-sentinel! nil)
      (let [observer (js/IntersectionObserver.
                      (fn [entries]
                        (when (some #(.-isIntersecting %) entries)
                          (dispatch [[:action/show-more-words]])))
                      #js {:root       (.closest node ".vocabulary__list")
                           :rootMargin "200px"})]
        (.observe observer node)
        (reset! sentinel-watch {:node node :observer observer})))))


(nxr/register-effect! :effect/unobserve-words-sentinel
  (fn unobserve-words-sentinel [{:keys [dispatch-data]} _]
    (disconnect-sentinel! (:replicant/node dispatch-data))))


(nxr/register-effect! :effect/update-word
  (fn ^:async update-word
    [{:keys [capabilities dispatch]} _ {:keys [id translation] :as opts} token]
    (try
      (await (vocabulary/update! capabilities id translation))
      (await (show! dispatch capabilities opts token))
      (catch js/Error err
        (log/error :effect/update-word {:error (str err)})))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word
    [{:keys [capabilities dispatch]} _ {:keys [id value] :as opts} token]
    (let [collection-id ((:collections/active-id (:collections capabilities)))
          prompt        (if collection-id
                          (str "Убрать «" value "» из набора?")
                          (str "Удалить «" value "» окончательно?"))]
      (when (js/confirm prompt)
        ;; The dialog closes here rather than on the rows that follow: rows
        ;; arrive for reasons of their own now, and declining the prompt above
        ;; leaves the reader where they were (#439).
        (dispatch [[:action/close-word-edit]])
        (try
          (await (vocabulary/remove-from-active! capabilities id))
          (await (show! dispatch capabilities opts token))
          (catch js/Error err
            (log/error :effect/delete-word {:error (str err)})))))))
