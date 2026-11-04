(ns invite-requests
  "A device without an account asks for an invite with the sha256 of a secret
   it keeps. The operator grants or declines it from an email, and a grant
   makes that secret the device's invite."
  (:require
   [cheshire.core :as cheshire]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [grants :as grants]
   [hiccup :as hiccup]
   [malli.core :as m]
   [malli.transform :as mt]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.client :as client]
   [taoensso.telemere :as t]
   [utils :as utils])
  (:import
   [java.net Inet6Address InetAddress]
   [java.util Arrays HexFormat]))


(set! *warn-on-reflection* true)


(def ^:private edge-spaces
  "The spaces at either end of a text, the invisible ones too: `str/trim`
   leaves NBSP and the zero-width spaces."
  #"(?U)^[\s\x{200B}-\x{200D}\x{2060}\x{FEFF}]+|[\s\x{200B}-\x{200D}\x{2060}\x{FEFF}]+$")


(def ^:private note-schema
  "The text a learner writes to the operator: its edges trimmed, then 1 to
   500 characters. Characters, not UTF-16 units: an emoji counts once."
  [:and
   [:string {:decode/request #(cond-> % (string? %) (str/replace edge-spaces ""))}]
   [:fn #(<= 1 (.codePointCount ^String % 0 (count %)) 500)]])


(def ^:private request-schema
  (m/schema
   [:map
    [:days nat-int?]
    [:note note-schema]
    [:sha256 [:re #"^[0-9a-f]{64}$"]]
    [:words nat-int?]]))


(defn from-body
  "The invite request `body` asks for — `{:days … :note … :sha256 … :words …}`,
   the note trimmed and every other key dropped — or nil when it is not one."
  [body]
  (let [request (m/decode request-schema
                          body
                          (mt/transformer mt/strip-extra-keys-transformer {:name :request}))]
    (when (m/validate request-schema request)
      request)))


;;
;; Per-address limit
;;


(def ^:private requests-per-day
  "How many requests one network makes in a day. Only a request that reached
   the operator counts: a refusal the caller could not help costs nothing."
  5)


(def ^:private day-ms (* 24 60 60 1000))


(def ^:private max-callers
  "How many networks the limit remembers at once. An attacker holding more
   addresses than this is refused instead of growing the memory."
  10000)


(def ^:private sweep-ms
  "How often the networks quiet for a day are forgotten: a sweep walks them
   all, so it runs this rarely and never once per request."
  60000)


(defonce ^:private recent-requests
  ;; network -> times of its requests in the last day. In memory: a restart
  ;; forgives everyone, which costs the operator a few mails at most.
  (atom {:request-times {} :swept-at 0}))


(defn- network
  "What one caller holds of `address`, or nil for no address: an IPv6 address
   is one of the 2^64 in its /64, which a single subscriber usually gets
   whole, and an IPv4 address written as IPv6 is that IPv4 address."
  [address]
  (let [ip (when (and address (re-matches #"[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*" address))
             (try (InetAddress/getByName address)
                  (catch Exception _
                    nil)))]
    (cond
      (instance? Inet6Address ip)
      (str (.formatHex (HexFormat/of) (Arrays/copyOf (.getAddress ^Inet6Address ip) 8)) "::/64")

      ip
      (.getHostAddress ^InetAddress ip)

      :else
      (utils/non-blank address))))


(defn- recent
  "Those of `request-times` that fall in the day before `now-ms`."
  [request-times now-ms]
  (filterv #(< (- now-ms day-ms) %) request-times))


(defn- swept
  "`state` with the networks quiet for a day forgotten, once in `sweep-ms`."
  [{:keys [swept-at] :as state} now-ms]
  (if (< (- now-ms swept-at) sweep-ms)
    state
    {:request-times (into {}
                          (keep (fn [[network request-times]]
                                  (some->> (recent request-times now-ms) not-empty (vector network))))
                          (:request-times state))
     :swept-at      now-ms}))


(defn admits?
  "True when `address` may make another request at `now-ms`. IPv6 addresses
   count by their /64; no address is admitted."
  [address now-ms]
  (let [network (network address)
        {:keys [request-times]} (swap! recent-requests swept now-ms)]
    (and (some? network)
         (< (count (recent (get request-times network) now-ms)) requests-per-day)
         (or (contains? request-times network) (< (count request-times) max-callers)))))


(defn count-request!
  "Counts a request of `address` made at `now-ms`. Callers at once may each
   pass `admits?` before either is counted; nginx keeps that to a few."
  [address now-ms]
  (let [network (network address)]
    (swap! recent-requests
      #(-> (swept % now-ms)
           (update-in [:request-times network] (fn [request-times] (conj (recent request-times now-ms) now-ms)))))))


(def ^:private mails-per-day
  "How many times a day the operator's Worker is called. A request whose mail
   did not go is not counted in the 50 a day, so this is what bounds those
   while the Worker is down or slow."
  100)


(defonce ^:private mail-budget
  ;; The UTC day and how many mails were tried on it.
  (atom {:day 0 :tried 0}))


(defn- mail-left?
  "True when `budget` has a mail left to try on the UTC day `day`."
  [budget day]
  (or (not= day (:day budget))
      (< (:tried budget) mails-per-day)))


;;
;; Mail
;;


(defn mailer
  "The function that mails the operator `{:subject … :text …}` and answers
   whether it went: through the Worker in `infra/cloudflare/invite-mail` when
   the environment names one, into the log in a source checkout that does not,
   and nowhere in a packaged app that does not."
  [env from-source?]
  (let [url    (utils/non-blank (get env "LEARNING_APP__INVITE_MAIL_URL"))
        secret (utils/non-blank (get env "LEARNING_APP__INVITE_MAIL_SECRET"))]
    (cond
      (and url secret)
      (fn [message]
        (let [{:keys [error status]} @(client/request
                                       {:body    (cheshire/generate-string message)
                                        :headers {"Authorization" (str "Bearer " secret)
                                                  "Content-Type"  "application/json"}
                                        :method  :post
                                        ;; `send` answers once Cloudflare took
                                        ;; the mail, about 4 s.
                                        :timeout 10000
                                        :url     url})]
          (or (and (nil? error) (<= 200 status 299))
              (do (t/event! ::mail-failed {:level :error :data {:error (some-> error ex-message) :status status}})
                  false))))

      from-source?
      (fn [message]
        (t/event! ::mail-logged {:level :info :data message})
        true)

      :else
      (fn [_message]
        (t/event! ::mail-unconfigured {:level :error})
        false))))


(defn- practice
  "\"312 words learned over 24 days of practice.\""
  [words days]
  (str words
       (if (= 1 words) " word" " words")
       " learned over "
       days
       (if (= 1 days) " day" " days")
       " of practice."))


(defn- mail-button
  [href class text {:keys [background color edge lip padding]}]
  [:td
   {:width "50%"
    :style {:padding padding}}
   [:a
    {:class class
     :href  href
     :style {:background      background
             :border          (str "2px solid " edge)
             :border-bottom   (str "4px solid " lip)
             :border-radius   "16px"
             :color           color
             :display         "block"
             :font-size       "17px"
             :font-weight     "800"
             :line-height     "46px"
             :text-align      "center"
             :text-decoration "none"}}
    text]])


(defn- invite-mail
  "The mail that brings the operator a request: an HTML part and a plain-text
   one. The links come before the note and the note is text, so a note cannot
   pass its own links off as the real ones."
  [links words days note]
  (let [font  "Nunito, -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"
        muted {:color "#6f6f6f" :font-size "17px" :line-height "1.5" :margin "0"}]
    {:subject "Sprecha: invite request"
     :text
     (format
      "A device asks for an invite.\n\nGrant: %s/grant\nDecline: %s/decline\n\nWords: %d\nDays of practice: %d\n\nThe learner wrote:\n\n%s\n"
      links
      links
      words
      days
      (->> (str/split-lines note)
           (map #(str "> " %))
           (str/join "\n")))
     :html
     (hiccup/render
      [:html
       {:lang "en"}
       [:head
        [:meta {:charset "UTF-8"}]
        [:meta
         {:name    "viewport"
          :content "width=device-width, initial-scale=1"}]
        [:meta
         {:name    "color-scheme"
          :content "light dark"}]
        [:meta
         {:name    "supported-color-schemes"
          :content "light dark"}]
        [:style
         (slurp (io/resource "invite-mail.css"))]]
       [:body
        {:style {:background "#f7f7f7" :margin "0" :padding "0"}}
        [:table.mail
         {:role        "presentation"
          :width       "100%"
          :cellpadding "0"
          :cellspacing "0"
          :style       {:background "#f7f7f7"}}
         [:tr
          [:td
           {:align "center"
            :style {:padding "24px 12px"}}
           [:table.mail__card
            {:role        "presentation"
             :width       "100%"
             :cellpadding "0"
             :cellspacing "0"
             :style       {:background    "#ffffff"
                           :border-radius "20px"
                           :color         "#3c3c3c"
                           :font-family   font
                           :max-width     "520px"}}
            [:tr
             [:td
              {:style {:padding "36px 28px 28px"}}
              [:p
               {:style {:font-size "15px" :font-weight "800" :margin "0 0 28px"}}
               (for [color ["#58cc02" "#a3d40a" "#f5d400"]]
                 [:span
                  {:style {:background    color
                           :border-radius "50%"
                           :display       "inline-block"
                           :height        "7px"
                           :margin-right  "3px"
                           :width         "7px"}}])
               [:span
                {:style {:margin-left "5px"}}
                "Sprecha"]]
              [:h1
               {:style {:font-size "26px" :font-weight "800" :line-height "1.2" :margin "0 0 8px"}}
               "Someone asks for an invite"]
              [:p.mail__muted
               {:style (assoc muted :margin "0 0 28px")}
               (practice words days)]
              [:table
               {:role        "presentation"
                :width       "100%"
                :cellpadding "0"
                :cellspacing "0"
                :style       {:margin "0 0 32px" :max-width "380px"}}
               [:tr
                (mail-button
                 (str links "/grant")
                 "mail__grant"
                 "Grant"
                 {:background "#58cc02" :color "#ffffff" :edge "#58cc02" :lip "#58a700" :padding "0 6px 0 0"})
                (mail-button
                 (str links "/decline")
                 "mail__decline"
                 "Decline"
                 {:background "#ffffff" :color "#6f4ea1" :edge "#e5e5e5" :lip "#e5e5e5" :padding "0 0 0 6px"})]]
              [:p.mail__label
               {:style {:color "#8a5fc7" :font-size "14px" :font-weight "700" :margin "0 0 10px"}}
               "They wrote"]
              [:p
               {:style {:font-size     "19px"
                        :line-height   "1.55"
                        :margin        "0 0 32px"
                        :overflow-wrap "anywhere"
                        :white-space   "pre-line"}}
               note]
              [:p.mail__muted.mail__rule
               {:style (assoc muted
                              :border-top  "2px solid #f0f0f0"
                              :font-size   "13px"
                              :padding-top "20px")}
               "Each button opens a page where you confirm. Opening it changes nothing."]]]]]]]]])}))


;;
;; Requests
;;


(def ^:private select-request
  "A request with its grant, if it got one: the grant tells a live invite from
   an expired one."
  "SELECT r.*, g.used_at, g.expires_at
     FROM invite_requests r LEFT JOIN grants g ON g.sha256 = r.sha256")


(defn- status
  "The status of a stored request. `:request/unsent` is one whose mail the
   Worker never took: the operator knows of it only if the mail went anyway."
  [{:keys [decision expires_at mailed_at used_at]}]
  (cond
    (and (nil? decision) (nil? mailed_at))
    :request/unsent

    (nil? decision)
    :request/pending

    (and (= "granted" decision) (nil? used_at) (some-> expires_at (<= (System/currentTimeMillis))))
    :request/expired

    :else
    (keyword "request" decision)))


(defn- hash-request
  "The request made with the device hash `sha256`, or nil."
  [db sha256]
  (jdbc/execute-one! db
    [(str select-request " WHERE r.sha256 = ?") sha256]
    {:builder-fn result-set/as-unqualified-maps}))


(defn token-request
  "The request whose mail carried `token`, or nil."
  [db token]
  (jdbc/execute-one! db
    [(str select-request " WHERE r.token_sha256 = ?") (utils/sha256-hex token)]
    {:builder-fn result-set/as-unqualified-maps}))


(def ^:private new-requests-per-day
  "How many new requests all callers together store in a day: the operator's
   mailbox and the Worker's mail quota bound what one attacker with many
   addresses can spend."
  50)


(def ^:private mail-wait-s
  "How long a request waits for its mail before another one may replace it:
   well past the mail's timeout."
  60)


(def ^:private store-request
  "Stores a new request, or replaces one whose grant expired unused or whose
   mail has not gone for `mail-wait-s`, while the day's cap allows. The cap
   counts mailed requests only: an unsent one never reached the operator. The
   count and the write are one statement, so callers at once cannot all slip
   under the cap or replace one request twice. Takes the sha256, the token's sha256,
   words, days, note, the cap, `mail-wait-s` and now in ms; returns a row when
   it stored one."
  "INSERT INTO invite_requests (sha256, token_sha256, words, days, note)
   SELECT ?, ?, ?, ?, ?
   WHERE (SELECT COUNT(*) FROM invite_requests
          WHERE created_at > UNIXEPOCH() - 86400 AND mailed_at IS NOT NULL) < ?
   ON CONFLICT (sha256) DO UPDATE
   SET token_sha256 = excluded.token_sha256, words = excluded.words,
       days = excluded.days, note = excluded.note,
       created_at = UNIXEPOCH(), mailed_at = NULL, decision = NULL
   WHERE (invite_requests.mailed_at IS NULL AND invite_requests.decision IS NULL
          AND invite_requests.created_at <= UNIXEPOCH() - ?)
      OR (invite_requests.decision = 'granted'
          AND EXISTS (SELECT 1 FROM grants g
                      WHERE g.sha256 = invite_requests.sha256
                        AND g.used_at IS NULL AND g.expires_at <= ?))
   RETURNING sha256")


(defn request!
  "Stores `request` of `address` and mails the operator its links. Returns
   `:outcome/created`, and counts it against `address`; `:failure/full` when
   the day's new requests or mails are used up, or `:failure/mail` when the
   mail did not go, or has not gone yet. A hash already requested mails
   nothing and answers its status. It is requested anew when its grant expired
   unused, or when its mail has not gone for `mail-wait-s`; nothing of the old
   request is lost before that.

   The mail is sent outside any transaction: SQLite's write lock held over an
   HTTP call would stall every other writer."
  [db base-url mail! address {:keys [days note sha256 words]}]
  (let [token        (utils/random-token)
        token-sha256 (utils/sha256-hex token)
        links        (str base-url "/api/invite-requests/" token)
        now-ms       (System/currentTimeMillis)
        day          (quot now-ms day-ms)
        stored?      (and (mail-left? @mail-budget day)
                          (jdbc/with-transaction [tx db]
                            (when (jdbc/execute-one! tx
                                    [store-request
                                     sha256 token-sha256 words days note
                                     new-requests-per-day mail-wait-s now-ms])
                              ;; The expired grant goes with the request it
                              ;; replaces, so a new grant can take its place.
                              (jdbc/execute! tx
                                ["DELETE FROM grants WHERE sha256 = ? AND used_at IS NULL AND expires_at <= ?"
                                 sha256 now-ms])
                              true)))]
    (cond
      (not stored?)
      (let [stored (some-> (hash-request db sha256) status)]
        ;; An expired request stays only when no new one could be made.
        (case stored
          (nil :request/expired) :failure/full
          :request/unsent        :failure/mail
          stored))

      ;; The try is counted before it is made, so callers at once cannot
      ;; all take the last one.
      (not (mail-left? (first (swap-vals! mail-budget
                                          (fn [budget]
                                            {:day   day
                                             :tried (inc (if (= day (:day budget)) (:tried budget) 0))})))
                       day))
      :failure/full

      ;; A mail that throws or fails leaves the request unsent: the next
      ;; request replaces it once `mail-wait-s` is over.
      (try
        (mail! (invite-mail links words days note))
        (catch Exception e
          (t/event! ::mail-failed {:level :error :data {:error (ex-message e)}})
          false))
      ;; Only a request that reached the operator counts against the
      ;; address: a retry after a refusal costs nothing.
      (do (jdbc/execute! db ["UPDATE invite_requests SET mailed_at = UNIXEPOCH() WHERE token_sha256 = ?" token-sha256])
          (count-request! address now-ms)
          :outcome/created)

      :else
      :failure/mail)))


(defn secret-status
  "The status of the request `sha256`, when `secret` is the one it was made
   with; nil otherwise, so a request shows nothing to whoever lacks its secret.
   An unsent request is none yet: the device asks again."
  [db sha256 secret]
  (when (= sha256 (utils/sha256-hex secret))
    (let [status (some-> (hash-request db sha256) status)]
      (when-not (= :request/unsent status)
        status))))


(defn decide!
  "Grants or declines, once, the request whose mail carried `token`. Returns
   the request as it stands afterwards, or nil when no mail carried the token.
   A grant makes the request's hash an invite."
  [db token decision]
  (jdbc/with-transaction [tx db]
    ;; The write goes first: a transaction that read first could not take the
    ;; write lock behind a second press of the button, and would fail instead
    ;; of waiting for it.
    (when-let
      [{:keys [sha256]}
       (jdbc/execute-one! tx
         ["UPDATE invite_requests SET decision = ?
           WHERE token_sha256 = ? AND decision IS NULL
           RETURNING sha256"
          (name decision) (utils/sha256-hex token)]
         {:builder-fn result-set/as-unqualified-maps})]
      (when (= :request/granted decision)
        (grants/store! tx sha256 grants/ttl-ms)))
    (token-request tx token)))


;;
;; The operator's page
;;


(defn page
  "The page a link opens, as a response for `hiccup/interceptor`: the request
   and the button that decides it as `decision`, or, once decided, how it was
   decided."
  [{:keys [days note words] :as request} decision]
  {:html/layout
   (fn [{:html/keys [body head]}]
     [:html
      {:lang "en"}
      [:head
       [:meta {:charset "UTF-8"}]
       [:meta
        {:name    "viewport"
         :content "width=device-width, initial-scale=1"}]
       head]
      [:body body]])
   :html/head
   [:<>
    [:meta
     {:name    "robots"
      :content "noindex"}]
    [:title "Invite request"]
    [:style
     (slurp (io/resource "invite-decision.css"))]]
   :html/body
   (let [wrote   [:div.invite-decision__quote
                  [:p.invite-decision__label "They wrote"]
                  [:p.invite-decision__note note]]
         stats   [:p.invite-decision__muted
                  (practice words days)]
         outcome (fn [mark path title line]
                   [:<>
                    [:div.invite-decision__outcome
                     [:span
                      {:class       ["invite-decision__mark" (str "invite-decision__mark--" mark)]
                       :aria-hidden "true"}
                      [:svg
                       {:width          "22"
                        :height         "22"
                        :viewBox        "0 0 24 24"
                        :fill           "none"
                        :stroke         "currentColor"
                        :stroke-width   "3"
                        :stroke-linecap "round"
                        :stroke-linejoin "round"}
                       [:path {:d path}]]]
                     [:h1.invite-decision__title title]]
                    (when line [:p.invite-decision__muted line])
                    wrote
                    stats])
         check   "M5 12.5l4.5 4.5L19 7.5"]
     [:main.invite-decision
      [:div.brand
       (for [color ["#58cc02" "#a3d40a" "#f5d400"]]
         [:span.brand__dot {:style {:background color}}])
       [:span.brand__name "Sprecha"]]
      (case (status request)
        ;; An unsent request has a page too: its mail can arrive after the
        ;; Worker's answer timed out.
        (:request/pending :request/unsent)
        (let [grant? (= :request/granted decision)]
          [:<>
           [:h1.invite-decision__title
            (if grant? "Grant this invite?" "Decline this invite?")]
           wrote
           stats
           [:form.invite-decision__form
            {:method "post"}
            [:button
             {:class ["invite-decision__button" (when-not grant? "invite-decision__button--decline")]
              :type  "submit"}
             (if grant? "Grant" "Decline")]]])
        :request/granted
        (outcome "granted" check "Granted" "The device can open its account now.")
        :request/expired
        (outcome "granted" check "Granted" "The invite expired unused.")
        :request/declined
        (outcome "declined" "M7 7l10 10M17 7L7 17" "Declined" nil))])})
