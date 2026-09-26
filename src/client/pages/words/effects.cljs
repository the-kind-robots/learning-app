(ns pages.words.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]))


;; The sentinel observer and the node it watches, kept together. Replicant
;; appends unmount hooks after mount hooks (`get-hooks-to-call`), so on a
;; subtree swap the new observer is registered before the old node's unmount
;; runs — a handle holding the observer alone would have that unmount
;; disconnect the new observer, and infinite scroll would die silently for the
;; rest of the visit (#439). The `:replicant/key` on the sentinel is what keeps
;; this rare; it is not what makes it safe.
(defonce ^:private sentinel-watch (atom nil))


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


(nxr/register-effect! :effect/scroll-words-to-top
  (fn scroll-words-to-top [_ _]
    (when-let [node (js/document.querySelector ".vocabulary__list")]
      (set! (.-scrollTop node) 0))))


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
    [{:keys [capabilities]} _ {:keys [id translation]}]
    (try
      (await (vocabulary/update! capabilities id translation))
      (catch js/Error err
        (log/error :effect/update-word {:error (str err)})))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word
    [{:keys [capabilities dispatch]} _ {:keys [id value]}]
    (let [collection-id ((:collections/active-id (:collections capabilities)))
          prompt        (if collection-id
                          (str "Убрать «" value "» из набора?")
                          (str "Удалить «" value "» окончательно?"))]
      (when (js/confirm prompt)
        ;; The dialog closes here rather than on the rows that follow: rows
        ;; change for reasons of their own, and declining the prompt above
        ;; leaves the reader where they were (#439).
        (dispatch [[:action/close-word-edit]])
        (try
          (await (vocabulary/remove-from-active! capabilities id))
          (catch js/Error err
            (log/error :effect/delete-word {:error (str err)})))))))
