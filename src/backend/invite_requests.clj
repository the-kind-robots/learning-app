(ns invite-requests
  "A device without an account asks for an invite with the sha256 of a secret
   it keeps. The operator grants or declines it from an email, and a grant
   makes that secret the device's invite."
  (:require
   [cheshire.core :as cheshire]
   [grants :as grants]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as result-set]
   [org.httpkit.client :as client]
   [taoensso.telemere :as t]
   [utils :as utils])
  (:import
   [java.net Inet6Address InetAddress]
   [java.util Arrays HexFormat]))


(set! *warn-on-reflection* true)


(defn invite-request
  "What a request body asks for: `{:sha256 … :words … :days …}`, or nil when
   the body is not a request."
  [body]
  (let [{:keys [days sha256 words]} (when (map? body) body)
        count? #(and (int? %) (<= 0 % 1000000))]
    (when (and (string? sha256)
               (re-matches #"[0-9a-f]{64}" sha256)
               (count? words)
               (count? days))
      {:days days :sha256 sha256 :words words})))


;;
;; Per-address limit
;;


(def ^:private requests-per-day 5)


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
  (atom {:swept-at 0 :times {}}))


(defn- network
  "What one caller holds of `address`: an IPv6 address is one of the 2^64 in
   its /64, which a single subscriber usually gets whole."
  [address]
  (let [ip (when (re-matches #"[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*" address)
             (try (InetAddress/getByName address)
                  (catch Exception _
                    nil)))]
    (if (instance? Inet6Address ip)
      (str (.formatHex (HexFormat/of) (Arrays/copyOf (.getAddress ^Inet6Address ip) 8)) "::/64")
      address)))


(defn admit!
  "True when `address` may make another request at `now-ms`, and counts it.
   IPv6 addresses count by their /64."
  [address now-ms]
  (let [network (network address)
        recent  (fn [times] (filterv #(< (- now-ms day-ms) %) times))]
    (:admitted?
     (swap! recent-requests
       (fn [{:keys [swept-at] :as state}]
         (let [{:keys [times] :as state}
               (if (< (- now-ms swept-at) sweep-ms)
                 state
                 {:swept-at now-ms
                  :times    (into {}
                                  (keep (fn [[network times]]
                                          (some->> (not-empty (recent times)) (vector network))))
                                  (:times state))})
               caller    (recent (get times network))
               admitted? (and (< (count caller) requests-per-day)
                              (or (contains? times network) (< (count times) max-callers)))]
           (cond-> (assoc state :admitted? admitted?)
             admitted? (assoc-in [:times network] (conj caller now-ms)))))))))


(def ^:private mails-per-day
  "How many times a day the operator's Worker is called. A request whose mail
   fails is not kept, so without this the 50 a day would never run out while
   the Worker is down or slow."
  100)


(defonce ^:private mail-budget
  ;; The UTC day and how many mails were tried on it.
  (atom {:day 0 :tried 0}))


(defn- spend-mail!
  "True when a mail may be tried at `now-ms`, and counts it."
  [now-ms]
  (let [day      (quot now-ms day-ms)
        [before] (swap-vals! mail-budget
                             (fn [{:keys [tried] :as budget}]
                               (let [tried (if (= day (:day budget)) tried 0)]
                                 {:day day :tried (min mails-per-day (inc tried))})))]
    (or (not= day (:day before))
        (< (:tried before) mails-per-day))))


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
                                        ;; The request waits for this answer on
                                        ;; one of the server's few threads.
                                        :timeout 3000
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


;;
;; Requests
;;


(def ^:private select-request
  "A request with its grant, if it got one: the grant tells a live invite from
   an expired one."
  "SELECT r.*, g.used_at, g.expires_at
     FROM invite_requests r LEFT JOIN grants g ON g.sha256 = r.sha256")


(defn- status
  "The status of a stored request."
  [{:keys [decision expires_at used_at]}]
  (cond
    (nil? decision)
    :request/pending

    (and (= "granted" decision) (nil? used_at) (<= expires_at (System/currentTimeMillis)))
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


(defn request!
  "Stores `request` and mails the operator its links. Returns `:outcome/created`,
   `:failure/full` when the day's new requests or mails are used up, or
   `:failure/mail` when the mail did not go; nothing is kept then, so the next
   request mails again. A hash already requested mails nothing and answers its
   status, unless its grant expired unused: then it is requested anew.

   The mail is sent outside any transaction: SQLite's write lock held over an
   HTTP call would stall every other writer."
  [db base-url mail! {:keys [days sha256 words]}]
  (when (= :request/expired (some-> (hash-request db sha256) status))
    (jdbc/with-transaction [tx db]
      (jdbc/execute! tx ["DELETE FROM grants WHERE sha256 = ?" sha256])
      (jdbc/execute! tx ["DELETE FROM invite_requests WHERE sha256 = ?" sha256])))
  (let [token (utils/random-token)
        links (str base-url "/api/invite-requests/" token)]
    (if
      ;; The count and the insert are one statement, so callers at once
      ;; cannot all slip under the cap.
      (jdbc/execute-one! db
        ["INSERT INTO invite_requests (sha256, token_sha256, words, days)
          SELECT ?, ?, ?, ?
          WHERE (SELECT COUNT(*) FROM invite_requests
                 WHERE created_at > UNIXEPOCH() - 86400) < ?
          ON CONFLICT DO NOTHING
          RETURNING sha256"
         sha256 (utils/sha256-hex token) words days new-requests-per-day])
      (let
        [outcome
         (cond
           (not (spend-mail! (System/currentTimeMillis)))
           :failure/full

           (mail!
            {:subject "Sprecha: invite request"
             :text
             (format
              "A device asks for an invite.\n\nWords: %d\nDays of practice: %d\n\nGrant: %s/grant\nDecline: %s/decline\n"
              words
              days
              links
              links)})
           :outcome/created

           :else
           :failure/mail)]
        (when-not (= :outcome/created outcome)
          (jdbc/execute! db ["DELETE FROM invite_requests WHERE sha256 = ?" sha256]))
        outcome)
      (if-let [stored (hash-request db sha256)]
        (status stored)
        :failure/full))))


(defn secret-status
  "The status of the request `sha256`, when `secret` is the one it was made
   with; nil otherwise, so a request shows nothing to whoever lacks its secret."
  [db sha256 secret]
  (when (= sha256 (utils/sha256-hex secret))
    (some-> (hash-request db sha256) status)))


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
  "The page a link opens: the request and the button that decides it as
   `decision`, or, once decided, how it was decided."
  [{:keys [days words] :as request} decision]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    [:meta {:name "robots" :content "noindex"}]
    [:title "Invite request"]
    [:link {:rel "stylesheet" :href "/css/base/typography.css"}]
    [:style
     "body{margin:0;background:#fff;color:#222;font-family:Nunito,system-ui,sans-serif}
   main{max-width:22rem;margin:0 auto;padding:3rem 1.25rem}
   h1{font-size:1.5rem;font-weight:800;margin:0 0 .75rem}
   p{font-size:1.125rem;line-height:1.5;margin:0 0 2rem}
   button{font:inherit;font-size:1.25rem;font-weight:800;color:#fff;border:0;border-radius:1rem;padding:1rem 2.5rem;cursor:pointer}
   button:focus-visible{outline:3px solid #222;outline-offset:3px}
   .granted{color:hsl(100 55% 32%)}
   .declined{color:hsl(280 45% 45%)}
   button.granted{color:#fff;background:hsl(100 55% 42%);box-shadow:0 4px 0 hsl(100 55% 30%)}
   button.declined{color:#fff;background:hsl(280 45% 52%);box-shadow:0 4px 0 hsl(280 45% 38%)}
   button:active{transform:translateY(4px);box-shadow:none}"]]
   [:body
    [:main
     [:h1 "Invite request"]
     [:p words " words, " days " days of practice."]
     (case (status request)
       :request/pending
       [:form {:method "post"}
        [:button {:class (name decision) :type "submit"}
         (if (= :request/granted decision) "Grant" "Decline")]]
       :request/granted [:p.granted "Granted."]
       :request/expired [:p.granted "Granted. The invite expired unused."]
       :request/declined [:p.declined "Declined."])]]])
