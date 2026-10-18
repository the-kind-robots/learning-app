(ns pages.words.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [use-cases.vocabulary :as vocabulary]))


;; The sentinel observer and its node, kept together: Replicant appends unmount hooks after mount
;; hooks, so on a subtree swap the new observer is registered before the old node's unmount runs, and
;; a handle holding the observer alone would disconnect the new one and kill infinite scroll silently.
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
  ;; The sentinel is keyed, so this runs once per visit. The root is the list, the box that clips the
  ;; sentinel: by default the viewport never clips it and `rootMargin` would widen nothing.
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
    [{:keys [capabilities dispatch]} _ {:keys [id translation]}]
    (try
      (await (vocabulary/update! capabilities id translation))
      (dispatch [[:effect/enter :action/refresh-page]])
      (catch js/Error err
        (log/error :effect/update-word {:error (str err)})))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word
    [{:keys [capabilities dispatch]} _ {:keys [confirm-message id]}]
    (when (js/confirm confirm-message)
      ;; The dialog closes here, not on the rows that follow: rows change for their own reasons, and
      ;; declining the prompt leaves the reader where they were.
      (dispatch [[:action/close-word-edit]])
      (try
        (await (vocabulary/remove-from-active! capabilities id))
        (dispatch [[:effect/enter :action/refresh-page]])
        (catch js/Error err
          (log/error :effect/delete-word {:error (str err)}))))))
