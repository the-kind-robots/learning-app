(ns application
  (:require
   ["qrcode" :as QRCode]
   [adapters.identity :as identity]
   [application.presenter :as presenter]
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
  (fn save [_ system m]
    (swap! (:store system) merge m)))


(nxr/register-effect! :effect/navigate
  (fn navigate-effect [{:keys [capabilities]} _ page]
    (when-let [navigate! (get-in capabilities [:navigation :navigation/navigate])]
      (navigate! page))))


(nxr/register-effect! :effect/sync-pull
  ;; Only a pull that wrote something changes the screen: a poke from a socket
  ;; reconnect, or the pull on route entry, brings nothing most of the time,
  ;; and reloading the page for it re-rendered the themes screen every ~2 min
  ;; on the phone. The pairing receipt is itself a pulled document, so the
  ;; same condition covers the dialog.
  (fn sync-pull [{:keys [capabilities dispatch]} _ & [reason]]
    (when-let [pull! (get-in capabilities [:capabilities/sync :sync/pull!])]
      (some-> (pull! reason)
              (.then (fn [{:keys [pulled]}]
                       (when (and pulled (pos? pulled))
                         (dispatch [[:action/reload-page] [:action/confirm-pairing]]))))))))


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


(nxr/register-effect! :effect/close-modal
  (fn close-modal [{:keys [dispatch-data]} _]
    (.close (:replicant/node dispatch-data))))


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


(defn- css-sized?
  "Whether the browser sizes this textarea itself through `field-sizing`.
   Asked by the CSS property name: `fieldSizing` is missing from Closure's
   externs, so the dotted accessor survives :advanced only as long as extern
   inference keeps resolving it. A string never has to be resolved."
  [node]
  (-> (js/getComputedStyle node)
      (.getPropertyValue "field-sizing")
      (= "content")))


(defn- autogrow!
  "Fits a textarea's height to its content: collapse, then measure. The
   fallback for Safari, which lacks `field-sizing: content` — where the
   property works, measuring only overrides a height the browser got right.
   `scrollHeight` omits the borders a border-box height must carry."
  [node]
  (when (and node (not (css-sized? node)))
    (set! (.. node -style -height) "auto")
    (let [borders (- (.-offsetHeight node) (.-clientHeight node))]
      (set! (.. node -style -height) (str (+ (.-scrollHeight node) borders) "px")))))


(nxr/register-effect! :effect/autogrow-target
  (fn autogrow-target [{:keys [dispatch-data]} _]
    (autogrow! (some-> dispatch-data :replicant/dom-event .-target))))


(nxr/register-effect! :effect/clear-autogrow
  ;; Hands the height back to CSS. The measured height is an inline style, so
  ;; nothing else clears it: an emptied field would keep the height its former
  ;; content earned.
  (fn clear-autogrow [_ _ element-id]
    (some-> (js/document.getElementById element-id) .-style (.removeProperty "height"))))


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


(nxr/register-action! :action/dismiss-on-backdrop
  (fn dismiss-on-backdrop [_ backdrop?]
    ;; A native modal renders its backdrop as part of the dialog element, so a
    ;; click that lands on the element itself came from outside the content.
    (when backdrop?
      [[:effect/close-modal]])))


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


(nxr/register-placeholder! :event.keyboard/meta?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-metaKey)))


(nxr/register-placeholder! :event.keyboard/shift?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-shiftKey)))


(nxr/register-placeholder! :event.keyboard/alt?
  (fn [dispatch-data]
    (some-> (:replicant/dom-event dispatch-data) .-altKey)))


(nxr/register-placeholder! :event/self-click?
  (fn [dispatch-data]
    (let [e (:replicant/dom-event dispatch-data)]
      (= (.-target e) (.-currentTarget e)))))


;; The modifier is what tells a submit from a newline in a multi-line field, so
;; `Cmd` counts too — it is the one a Mac keyboard offers. The default is
;; suppressed because a browser that submits on `Ctrl`+`Enter` by itself would
;; submit the form twice.
(nxr/register-action! :action/submit-if-ctrl-enter
  (fn submit-if-ctrl-enter [_ {:keys [key ctrl? meta?]}]
    (when (and (= "Enter" key) (or ctrl? meta?))
      [[:effect/prevent-default]
       [:effect/request-submit]])))


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


(nxr/register-action! :action/confirm-pairing
  (fn confirm-pairing [state]
    (when-some [nonce (get-in state [:app/pairing :nonce])]
      [[:effect/confirm-pairing nonce]])))


(nxr/register-effect! :effect/confirm-pairing
  ;; Runs after every pull while the dialog waits. Only the receipt carrying
  ;; this dialog's own nonce closes it — pokes from ordinary writes on other
  ;; devices and socket reconnects change nothing.
  (fn ^:async confirm-pairing
    [{:keys [capabilities dispatch]} _ nonce]
    (when-some [confirmed! (get-in capabilities [:capabilities/sync :sync/pairing-confirmed!])]
      (when (await (confirmed! nonce))
        (dispatch [[:action/close-pairing-dialog]])))))


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
        ;; The nonce tags one pairing round: the QR carries it out, the new
        ;; device echoes it back as a receipt, and only that echo closes
        ;; this dialog.
        (let [nonce    (js/crypto.randomUUID)
              pair-url (str (account-key-url identity) "&pair=" nonce)
              qr-url   (await (.toDataURL QRCode pair-url))]
          (dispatch [[:action/show-pairing-dialog
                      {:nonce nonce :pair-url pair-url :qr-url qr-url}]])))
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


(defn- trace-export-icon
  []
  [:svg.app-shell__icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:path {:d "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"}]
   [:polyline {:points "14 3 14 8 19 8"}]
   [:line {:x1 "12" :y1 "11" :x2 "12" :y2 "17"}]
   [:polyline {:points "9 14 12 17 15 14"}]])


(defn- devices-icon
  "A laptop beside a phone: connecting devices, not reloading the page."
  []
  [:svg.app-shell__icon
   {:aria-hidden    "true"
    :fill           "none"
    :stroke         "currentColor"
    :stroke-linecap "round"
    :stroke-linejoin "round"
    :stroke-width   "1.8"
    :viewBox        "0 0 24 24"}
   [:path {:d "M13 14H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h11a1 1 0 0 1 1 1v1"}]
   [:path {:d "M1.5 18H13"}]
   [:rect {:x "16" :y "9" :width "6" :height "11" :rx "1.5"}]
   [:path {:d "M19 17.2h0"}]])


(defn- sync-menu-dialog
  [{:keys [show-account-actions?]}]
  [:dialog.sync-menu-dialog.modal
   {:replicant/on-mount [[:action/open-dialog]]
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-sync-menu]]}}
   [:div.sync-menu-dialog__content
    (when show-account-actions?
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
    :on {:click [[:action/dismiss-on-backdrop [:event/self-click?]]]
         :close [[:action/close-pairing-dialog]]}}
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
  (let [{:keys [build-mark menu-open? page pairing show-install? show-sync? show-update?]}
        (presenter/shell-props state)]
    (list
     ;; One bar across the top holds the three slots: the word mark, the build
     ;; mark and the actions. It is fixed, as the word mark and the actions
     ;; were on their own, so no page's content moves for it; the side slots
     ;; share the free space equally, which is what centres the build mark in
     ;; the bar rather than between its neighbours.
     [:div.app-shell__bar
      [:div.app-shell__bar-slot
       [:a.app-shell__logo {:href "/home"}
        "Sprecha"
        ;; A development build's word mark ends in a red D. A letter of the
        ;; name, nothing to tap; the ^boolean on the flag is what lets Closure
        ;; drop it, and the build mark below, from a release.
        (when ^boolean goog/DEBUG
          [:span.app-shell__dev-mark "D"])]]
      ;; Which bundle the page loaded, and the tap that reloads onto the
      ;; newest one: a check for a new build, taking it if one waits or is
      ;; installing, otherwise a plain reload.
      (when ^boolean goog/DEBUG
        [:button.app-shell__build-mark
         {:type       "button"
          :aria-label "Перезагрузить сборку"
          :title      "Перезагрузить сборку"
          :on         {:click [[:effect/get-newest-build]]}}
         build-mark])
      [:div.app-shell__actions
       ;; The trace export leads the row: a development build's control among
       ;; the shell's own, not a character beside the word mark.
       (when ^boolean goog/DEBUG
         [:button.app-shell__icon-button
          {:type       "button"
           :title      "Экспортировать трассу"
           :aria-label "Экспортировать трассу"
           :on         {:click [[:effect/export-trace]]}}
          (trace-export-icon)])
       ;; A new build waits until asked (ADR-0014); this is the asking.
       (when show-update?
         [:button.app-shell__text-button
          {:type  "button"
           :title "Обновить приложение"
           :on    {:click [[:effect/take-new-build]]}}
          "Обновить"])
       ;; Install stands on its own — it is not a sync action, and it is offered
       ;; before any account exists.
       (when show-install?
         [:button.app-shell__icon-button
          {:type       "button"
           :title      "Установить приложение"
           :aria-label "Установить приложение"
           :on         {:click [[:action/pwa-install-requested]]}}
          (install-icon)])
       (when show-sync?
         [:button.app-shell__icon-button
          {:type       "button"
           :title      "Синхронизация"
           :aria-label "Синхронизация"
           :on         {:click [[:action/open-sync-menu]]}}
          (devices-icon)])
       (case page
         :page/home        (collections-icon)
         :page/collections (close-icon)
         nil)]]
     (install-guide/render state)
     (when menu-open?
       (sync-menu-dialog (presenter/sync-menu-props state)))
     (when pairing
       (pairing-dialog pairing))
     (case page
       :page/collections (pages.collections.view/page state)
       :page/home        (pages.home.view/page state)
       :page/lesson      (pages.lesson.view/page state)
       :page/words       (pages.words.view/page state)
       [:div.app-loading "Загружаем..."]))))


(defn render!
  [state]
  (r/render js/document.body (render state))
  (sync-virtual-keyboard!))


;; One render per dispatch that changed state, none for a dispatch that
;; changed nothing. The store watch renders immediately outside a dispatch —
;; external writes keep rendering — and only marks dirty inside one;
;; `:after-dispatch` back at depth zero renders once if anything got dirty.
;; A counter, not a flag: effects dispatch actions from inside a dispatch
;; (dialog on-mount), and an async continuation arrives as a new top-level
;; dispatch — both must keep the guard up until their own dispatch unwinds.
(defn install-render!
  "Renders on every state change and only on change. `identical?` is enough:
   CLJS `assoc`/`merge` hand back the same map when nothing differs, so a
   dispatch that saves nothing costs no render. A dispatch that saves several
   times renders several times — measured earlier: extra renders inside one
   synchronous dispatch cost diffing, never extra paints, and the owner chose
   this simplicity over a coalescing counter (#213)."
  [store render-fn]
  (add-watch store
             ::render
             (fn [_ _ old state]
               (when-not (identical? old state)
                 (render-fn state)))))


(defn routes
  [dispatch]
  [["/home"
    {:name        :page/home
     :controllers [{:start #(dispatch [[:effect/load-home] [:effect/sync-pull]])}]}]
   ["/words"
    {:name        :page/words
     ;; Entering the screen opens no dialog. The rows no longer clear
     ;; `:words/editing` on their way in (#439), so leaving the screen with a
     ;; word open would otherwise bring it back on the return.
     :controllers [{:start #(dispatch [[:action/close-word-edit]
                                       [:action/load-words]
                                       [:effect/sync-pull]])}]}]
   ["/lesson"
    {:name        :page/lesson
     :controllers [{:start #(dispatch [[:effect/load-lesson] [:effect/sync-pull]])}]}]
   ["/collections"
    {:name        :page/collections
     :controllers [{:start #(dispatch [[:action/open-collections]
                                       [:effect/load-collections]
                                       [:effect/sync-pull]])}]}]])
