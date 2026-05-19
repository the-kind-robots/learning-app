(ns application
  (:require
   [install-guide.view :as install-guide]
   [nexus.registry :as nxr]
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


(defn- render
  [state]
  (list
   [:a.app-shell__logo {:href "/home"} "Sprecha"]
   (install-guide/render state)
   (case (:app/page state)
     :page/home   (pages.home.view/page state)
     :page/words  (pages.words.view/page state)
     :page/lesson (pages.lesson.view/page state)
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
     :controllers [{:start #(dispatch [[:effect/load-lesson]])}]}]])
