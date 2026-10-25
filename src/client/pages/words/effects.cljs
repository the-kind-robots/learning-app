(ns pages.words.effects
  (:require
   [lambdaisland.glogi :as log]
   [nexus.registry :as nxr]
   [ports.examples-generator :as examples-generator]
   [use-cases.examples :as examples]
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


(nxr/register-effect! :effect/write-word
  (fn ^:async write-word
    [{{:keys [learner world]} :capabilities :keys [dispatch]} _ {:keys [id renamed? translation value]}]
    (try
      (await ((if renamed? vocabulary/rename! vocabulary/update!) learner world id value translation))
      (dispatch [[:effect/enter :action/refresh-page]])
      (catch js/Error err
        (log/error :effect/write-word {:error (str err)})))))


(nxr/register-effect! :effect/replace-example
  ;; One after another, then the dialog that waits for them closes, however they went.
  (fn ^:async replace-example
    [{{:keys [learner]} :capabilities :keys [dispatch]} _ replacements]
    (try
      (doseq [replacement replacements]
        (when-not (await (examples/replace! learner replacement))
          (log/error :effect/replace-example
                     {:collection-id (:collection-id replacement)
                      :word-id       (:word-id replacement)})))
      (catch js/Error err
        (log/error :effect/replace-example {:error (str err)}))
      (finally
       (dispatch [[:action/close-word-edit]])))))


;; The request that is out, to be aborted when the dialog closes.
(defonce ^:private request (atom nil))


(nxr/register-effect! :effect/abort-example-request
  (fn abort-example-request [_ _]
    (some-> ^js @request .abort)))


(nxr/register-effect! :effect/request-another-example
  ;; Asks for another example and hands the answer, or the failure, to the dialog.
  (fn ^:async request-another-example
    [{{:keys [examples-generator]} :capabilities :keys [dispatch]} _ {:keys [avoid subject word-id]}]
    (let [controller (js/AbortController.)
          _ (reset! request controller)
          answer     (try
                       (await (examples-generator/regenerate examples-generator subject avoid (.-signal controller)))
                       (finally
                        (reset! request nil)))]
      (dispatch [[:action/example-arrived word-id answer]]))))


(nxr/register-effect! :effect/delete-word
  (fn ^:async delete-word
    [{{:keys [learner]} :capabilities :keys [dispatch]} _ {:keys [confirm-message id]}]
    (when (js/confirm confirm-message)
      ;; The dialog closes here, not on the rows that follow: rows change for their own reasons, and
      ;; declining the prompt leaves the reader where they were.
      (dispatch [[:action/close-word-edit]])
      (try
        (await (vocabulary/remove-from-active! learner id))
        (dispatch [[:effect/enter :action/refresh-page]])
        (catch js/Error err
          (log/error :effect/delete-word {:error (str err)}))))))
