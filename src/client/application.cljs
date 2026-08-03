(ns application
  (:require
   ["qrcode" :as QRCode]
   [adapters.identity :as identity]
   [install-guide.view :as install-guide]
   [lambdaisland.glogi :as log]
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


(nxr/register-effect! :effect/sync-pull
  (fn sync-pull [{:keys [capabilities dispatch]} _]
    (when-let [pull! (get-in capabilities [:capabilities/sync :sync/pull!])]
      (some-> (pull!)
              (.then #(dispatch [[:action/reload-page]]))))))


(nxr/register-action! :action/reload-page
  (fn reload-page [state]
    (when-let [load-effect (:page/load state)]
      [load-effect])))


(nxr/register-effect! :effect/load-account
  (fn load-account [{:keys [capabilities dispatch]} _]
    (dispatch
     [[:effect/save
       {:app/account-id (get-in capabilities [:capabilities/sync :sync/account-id])}]])))


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
    (let [node (:replicant/node dispatch-data)
          len  (.-length (.-value node))]
      (.focus node)
      (.setSelectionRange node len len))))


(nxr/register-effect! :effect/select-all
  (fn select-all [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/node .select)))


(nxr/register-effect! :effect/blur-target
  (fn blur-target [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .-target .blur)))


(nxr/register-effect! :effect/set-target-text
  (fn set-target-text [{:keys [dispatch-data]} _ text]
    (when-let [target (some-> dispatch-data :replicant/dom-event .-target)]
      (set! (.-textContent target) (or text "")))))


(nxr/register-effect! :effect/prevent-default
  (fn prevent-default [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .preventDefault)))


(nxr/register-effect! :effect/stop-propagation
  (fn stop-propagation [{:keys [dispatch-data]} _]
    (some-> dispatch-data :replicant/dom-event .stopPropagation)))


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


(nxr/register-placeholder! :event.target/text-content
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-target .-textContent)))


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


(nxr/register-action! :action/open-sync-menu
  (fn open-sync-menu [_]
    [[:effect/save {:app/sync-menu-open? true}]]))


(nxr/register-action! :action/close-sync-menu
  (fn close-sync-menu [_]
    [[:effect/save {:app/sync-menu-open? false}]]))


(nxr/register-action! :action/show-pairing-dialog
  (fn show-pairing-dialog [_ pairing]
    [[:effect/save
      {:app/sync-menu-open? false
       :app/pairing pairing}]]))


(nxr/register-action! :action/close-pairing-dialog
  (fn close-pairing-dialog [_]
    [[:effect/save {:app/pairing nil}]]))


(defn- account-key-url
  "The QR/recovery URL a device opens to adopt this account. The token rides in
   the fragment, which browsers never send to the server, keeping it out of
   access logs and Referer headers (ADR-0006)."
  [{:keys [token]}]
  (str (.. js/window -location -origin) "/#key=" token))


(nxr/register-effect! :effect/create-recovery-link
  (fn ^:async create-recovery-link
    [_ _]
    (try
      (when-let [identity (await (identity/load-identity!))]
        (let [url (account-key-url identity)]
          (if (exists? js/navigator.share)
            (await (js/navigator.share #js {:title "Sprecha: восстановление доступа" :url url}))
            (do
              (await (.. js/navigator -clipboard (writeText url)))
              (js/alert "Ссылка скопирована в буфер обмена")))))
      (catch js/Error err
        (log/error :effect/create-recovery-link {:error (str err)})))))


(nxr/register-effect! :effect/open-pairing
  (fn ^:async open-pairing
    [{:keys [dispatch]} _]
    (try
      (when-let [identity (await (identity/load-identity!))]
        (let [pair-url (account-key-url identity)
              qr-url   (await (.toDataURL QRCode pair-url))]
          (dispatch [[:action/show-pairing-dialog {:qr-url qr-url :pair-url pair-url}]])))
      (catch js/Error err
        (log/error :effect/open-pairing {:error (str err)})))))


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


(defn- install-icon
  []
  [:svg.app-shell__icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:path {:d "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"}]
   [:polyline {:points "7 10 12 15 17 10"}]
   [:line {:x1 "12" :y1 "15" :x2 "12" :y2 "3"}]])


(defn- sync-icon
  []
  [:svg.app-shell__icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:polyline {:points "23 4 23 10 17 10"}]
   [:polyline {:points "1 20 1 14 7 14"}]
   [:path {:d "M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"}]])


(defn- sync-menu-dialog
  [state]
  [:dialog.sync-menu-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:close [[:action/close-sync-menu]]}}
   [:div.sync-menu-dialog__content
    (when (:app/account-id state)
      (list
       [:button.sync-menu-dialog__item
        {:type "button"
         :on   {:click [[:effect/open-pairing]]}}
        "Подключить устройство"]
       [:button.sync-menu-dialog__item
        {:type "button"
         :on   {:click [[:effect/create-recovery-link]]}}
        "Ссылка восстановления"]))
    [:button.sync-menu-dialog__item.sync-menu-dialog__item--cancel
     {:type "button"
      :on   {:click [[:action/close-sync-menu]]}}
     "Отмена"]]])


(defn- pairing-dialog
  [{:keys [qr-url pair-url]}]
  [:dialog.pairing-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:close [[:action/close-pairing-dialog]]}}
   [:div.pairing-dialog__content
    [:h2.pairing-dialog__title "Подключить устройство"]
    [:p.pairing-dialog__hint "Отсканируйте QR-код на новом устройстве"]
    [:img.pairing-dialog__qr {:src qr-url :alt "QR-код для подключения устройства"}]
    [:button.pairing-dialog__close
     {:type "button"
      :on   {:click [[:action/close-pairing-dialog]]}}
     "Закрыть"]]])


(defn- render
  [state]
  (list
   [:a.app-shell__logo {:href "/home"} "Sprecha"]
   [:div.app-shell__actions
    ;; Install stands on its own — it is not a sync action, and it is offered
    ;; before any account exists.
    (when (:pwa/install-available? state)
      [:button.app-shell__icon-button
       {:type       "button"
        :title      "Установить приложение"
        :aria-label "Установить приложение"
        :on         {:click [[:action/pwa-install-requested]]}}
       (install-icon)])
    ;; Sync actions are invite-gated (ADR-0006): no account, no entry point.
    (when (:app/account-id state)
      [:button.app-shell__icon-button
       {:type       "button"
        :title      "Синхронизация"
        :aria-label "Синхронизация"
        :on         {:click [[:action/open-sync-menu]]}}
       (sync-icon)])
    (case (:page/current state)
      :page/home        (collections-icon)
      :page/collections (close-icon)
      nil)]
   (install-guide/render state)
   (when (:app/sync-menu-open? state)
     (sync-menu-dialog state))
   (when (:app/pairing state)
     (pairing-dialog (:app/pairing state)))
   (case (:page/current state)
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
     :controllers [{:start #(dispatch [[:effect/load-words] [:effect/sync-pull]])}]}]
   ["/lesson"
    {:name        :page/lesson
     :controllers [{:start #(dispatch [[:effect/load-lesson] [:effect/sync-pull]])}]}]
   ["/collections"
    {:name        :page/collections
     :controllers [{:start #(dispatch [[:effect/load-collections] [:effect/sync-pull]])}]}]])
