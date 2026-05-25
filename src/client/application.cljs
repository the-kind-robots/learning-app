(ns application
  (:require
   [install-guide.view :as install-guide]
   [nexus.registry :as nxr]
   [pages.collections.view :as pages.collections.view]
   [pages.home.view :as pages.home.view]
   [pages.lesson.view :as pages.lesson.view]
   [pages.words.view :as pages.words.view]
   [replicant.dom :as r]))


;;
;; Interceptors
;;


(nxr/register-interceptor!
  :before-effect
  (fn [{:keys [system] :as ctx}]
    (assoc ctx :capabilities (:capabilities system))))


;;
;; Effects
;;


(nxr/register-effect! :effect/save
  ^:nexus/batch
  (fn save [_ system ms]
    (swap! (:store system) #(reduce merge % (map first ms)))))


(nxr/register-effect! :effect/navigate
  (fn navigate-effect [{:keys [capabilities]} _ page]
    (when-let [navigate! (get-in capabilities [:navigation :navigation/navigate])]
      (navigate! page))))


(nxr/register-effect! :effect/show-modal
  (fn show-modal [{:keys [dispatch-data]} _]
    (.showModal (:replicant/node dispatch-data))))


(nxr/register-effect! :effect/focus-child
  (fn focus-child [{:keys [dispatch-data]} _ selector]
    (some-> (.querySelector (:replicant/node dispatch-data) selector) .focus)))


(nxr/register-effect! :effect/mobile-autofocus
  (fn focus-child-pointer-fine [_ _ element-id]
    (when (.. js/window (matchMedia "(pointer:fine)") -matches)
      (some-> (js/document.getElementById element-id) .focus))))


(nxr/register-effect! :effect/focus
  (fn focus [_ _ element-id]
    (some-> (js/document.getElementById element-id) .focus)))


(nxr/register-effect! :effect/cursor-to-end
  (fn cursor-to-end [{:keys [dispatch-data]} _]
    (let [node (:replicant/node dispatch-data)]
      (.setSelectionRange node (.-length (.-value node)) (.-length (.-value node))))))


(nxr/register-effect! :effect/select-all
  (fn select-all [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/node .select)))


(nxr/register-effect! :effect/blur-target
  (fn blur-target [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .-target .blur)))


(nxr/register-effect! :effect/prevent-default
  (fn prevent-default [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .preventDefault)))


(nxr/register-effect! :effect/request-submit
  (fn request-submit [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .-target .-form .requestSubmit)))


(nxr/register-effect! :effect/click-target
  (fn click-target [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .-target .click)))


(nxr/register-effect! :effect/scroll-nearest
  (fn scroll-nearest [_ _ selector]
    (some-> (js/document.querySelector selector)
            (.scrollIntoView #js {:block "nearest"}))))


;;
;; Actions
;;


(nxr/register-action! :action/click-if-enter
  (fn click-if-enter [_ key]
    (when (= "Enter" key)
      [[:effect/click-target]])))


(nxr/register-action! :action/open-dialog
  (fn open-dialog [_]
    [[:effect/show-modal]]))


(nxr/register-action! :action/move-cursor-to-end
  (fn move-cursor-to-end [_]
    [[:effect/cursor-to-end]]))


(nxr/register-action! :action/select-all
  (fn select-all [_]
    [[:effect/select-all]]))


;;
;; Placeholder
;;


(nxr/register-placeholder! :event.target/value
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-target .-value)))


(nxr/register-placeholder! :event.form.field/value
  (fn [dispatch-data field-name]
    (some-> (:replicant/dom-event dispatch-data) .-target .-elements (.namedItem field-name) .-value)))


(nxr/register-placeholder! :event.keyboard/key
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-key)))


(nxr/register-placeholder! :event.keyboard/ctrl?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-ctrlKey)))


(nxr/register-placeholder! :event.keyboard/shift?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-shiftKey)))


(nxr/register-placeholder! :event/self-click?
  (fn [dispatch-data]
    (let [e (:replicant/dom-event dispatch-data)]
      (= (.-target e) (.-currentTarget e)))))


(nxr/register-action! :action/submit-if-ctrl-enter
  (fn submit-if-ctrl-enter [_ {:keys [key ctrl?]}]
    (when (and (= "Enter" key) ctrl?)
      [[:effect/request-submit]])))


;;
;; Render
;;


(defn sync-virtual-keyboard!
  []
  (when (js-in "virtualKeyboard" js/navigator)
    (when-let [vk (.-virtualKeyboard js/navigator)]
      (set! (.-overlaysContent vk)
            (boolean (js/document.querySelector "[data-vk-overlay]"))))))


(defn- collections-icon
  []
  [:a.app-shell__corner-icon
   {:href       "/collections"
    :aria-label "Открыть наборы"
    :title      "Наборы"}
   [:svg.app-shell__corner-icon-svg
    {:viewBox "0 0 16 16" :aria-hidden "true"}
    [:rect {:x 2 :y 2 :width 4 :height 4 :rx 1}]
    [:rect {:x 10 :y 2 :width 4 :height 4 :rx 1}]
    [:rect {:x 2 :y 10 :width 4 :height 4 :rx 1}]
    [:rect {:x 10 :y 10 :width 4 :height 4 :rx 1}]]])


(defn- close-icon
  []
  [:a.app-shell__corner-icon
   {:href       "/home"
    :aria-label "Закрыть"
    :title      "Закрыть"}
   [:svg.app-shell__corner-icon-svg
    {:viewBox "0 0 16 16" :aria-hidden "true"}
    [:path
     {:d "M4 4 L12 12 M12 4 L4 12"
      :stroke "currentColor"
      :stroke-width 2
      :stroke-linecap "round"}]]])


(defn- render
  [state]
  (list
   [:a.app-shell__logo {:href "/home"} "Sprecha"]
   (case (:app/page state)
     :page/home        (collections-icon)
     :page/collections (close-icon)
     (list))
   (install-guide/render state)
   (case (:app/page state)
     :page/collections (pages.collections.view/page state)
     :page/home        (pages.home.view/page state)
     :page/lesson      (pages.lesson.view/page state)
     :page/words       (pages.words.view/page state)
     [:div.app-loading "Загружаем..."])))


(defn render!
  [state]
  (r/render js/document.body (render state))
  (sync-virtual-keyboard!))


(defn routes
  [dispatch]
  [["/home"
    {:name        :page/home
     :controllers [{:start #(dispatch [[:effect/load-home]])}]}]
   ["/words"
    {:name        :page/words
     :controllers [{:start #(dispatch [[:effect/load-words]])}]}]
   ["/lesson"
    {:name        :page/lesson
     :controllers [{:start #(dispatch [[:effect/load-lesson]])}]}]
   ["/collections"
    {:name        :page/collections
     :controllers [{:start #(dispatch [[:effect/load-collections]])}]}]])
