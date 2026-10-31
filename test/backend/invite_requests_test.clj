(ns backend.invite-requests-test
  (:require
   [backend.support.db :as support.db]
   [cheshire.core :as cheshire]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [core :as core]
   [db :as db]
   [invite-requests :as sut]
   [next.jdbc :as jdbc]
   [org.httpkit.client :as client]
   [org.httpkit.server :as server]
   [utils :as utils]))


(set! *warn-on-reflection* true)


(def ^:private secret "the-device-secret")


(def ^:private stats
  {:days 14 :sha256 (utils/sha256-hex secret) :words 120})


(defn- forget-limits!
  []
  (reset! @#'sut/recent-requests {:swept-at 0 :times {}})
  (reset! @#'sut/mail-budget {:day 0 :tried 0}))


(defn- with-server
  "Runs `f` against a server on a fresh database with a map of:
   `:ask` (method path & [body address]) → response, `:mails` the operator
   was sent, `:couch` the CouchDB calls made, `:db`. `:mail-goes?` and
   `:couch-up?` are atoms that decide whether mail and CouchDB work."
  [f]
  (let [db         (support.db/migrated-db "invite-requests-test")
        mails      (atom [])
        couch      (atom [])
        mail-goes? (atom true)
        couch-up?  (atom true)]
    (forget-limits!)
    (with-redefs [core/db-spec db
                  core/mail-operator! (fn [message]
                                        (swap! mails conj message)
                                        @mail-goes?)
                  db/exists?   (fn [name] (swap! couch conj [:exists? name]) false)
                  db/use       identity
                  db/secure    (fn [name _]
                                 (swap! couch conj [:secure name])
                                 (when-not @couch-up?
                                   (throw (ex-info "couch is down" {}))))]
      (core/start-server! core/app-handler 0)
      (try
        (let [port (server/server-port @core/server)]
          (f {:ask        (fn [method path & [body address headers]]
                            @(client/request
                              (cond-> {:method  method
                                       :url     (str "http://localhost:" port path)
                                       :headers (merge {"X-Real-IP" (or address "203.0.113.1")} headers)}
                                body (-> (assoc :body (if (string? body) body (cheshire/generate-string body)))
                                         (assoc-in [:headers "Content-Type"] "application/json")))))
              :couch      couch
              :couch-up?  couch-up?
              :db         db
              :mail-goes? mail-goes?
              :mails      mails}))
        (finally
         (core/stop-server!))))))


(defn- answer
  "The status a JSON answer names."
  [response]
  (:status (cheshire/parse-string (:body response) true)))


(defn- request-invite!
  [ask body & [address]]
  (ask :post "/api/invite-requests" body address))


(defn- read-request
  "GETs the request made with `secret`, presenting `presented` as the secret."
  ([ask secret]
   (read-request ask secret secret))
  ([ask secret presented]
   (ask :get
        (str "/api/invite-requests/" (utils/sha256-hex secret))
        nil
        nil
        (when presented {"Authorization" (str "Bearer " presented)}))))


(defn- status-of
  "What reading the request made with `secret` says: its status, or the HTTP
   code when there is none."
  [ask secret]
  (let [response (read-request ask secret)]
    (if (= 200 (:status response))
      (answer response)
      (:status response))))


(defn- provision!
  [ask secret]
  (:status (ask :post "/api/identity/provision" {:invite secret})))


(defn- link
  "The path of the link that `decision` (\"Grant\" or \"Decline\") names in `mail`."
  [{:keys [text]} decision]
  (second (re-find (re-pattern (str decision ": https?://[^/]+(\\S+)")) text)))


(deftest a-request-is-a-hash-and-two-counts-and-nothing-else
  (is (= stats (sut/invite-request (assoc stats :name "Anna"))))
  (is (= (assoc stats :days 1000000 :words 0)
         (sut/invite-request (assoc stats :days 1000000 :words 0))))
  (are [body] (nil? (sut/invite-request body))
   nil
   "text"
   (dissoc stats :words)
   (assoc stats :sha256 (str/upper-case (:sha256 stats)))
   (assoc stats :sha256 "abc")
   (assoc stats :days -1)
   (assoc stats :days 1000001)
   (assoc stats :words 1.5)
   (assoc stats :words "120")))


(deftest an-address-makes-five-requests-a-day
  (forget-limits!)
  (let [day (* 24 60 60 1000)]
    (is (= [true true true true true false]
           (mapv #(sut/admit! "a" %) [0 1 2 3 4 5])))
    (testing "a request counts for a day"
      (is (false? (sut/admit! "a" (dec day))))
      (is (true? (sut/admit! "a" (inc day)))))
    (testing "another address has its own five"
      (is (true? (sut/admit! "b" 6))))))


(deftest an-ipv6-network-counts-as-one-address
  (forget-limits!)
  (is (= [true true true true true false]
         (mapv #(sut/admit! (str "2001:db8:1:2::" %) %) [1 2 3 4 5 6])))
  (is (false? (sut/admit! "2001:0db8:0001:0002:ffff:ffff:ffff:ffff" 7))
      "the same /64 written in full")
  (is (true? (sut/admit! "2001:db8:1:3::1" 8)) "the next /64 is another caller")
  (is (true? (sut/admit! "203.0.113.9" 9)) "IPv4 counts by address")
  (is (true? (sut/admit! ":::::::::" 10)) "an address that does not parse counts as written"))


(deftest the-limit-remembers-a-bounded-number-of-networks
  (forget-limits!)
  (with-redefs [sut/max-callers 3]
    (is (= [true true true false]
           (mapv #(sut/admit! (str "203.0.113." %) %) [1 2 3 4]))
        "a new network past the bound is refused")
    (is (true? (sut/admit! "203.0.113.1" 5)) "a known one still counts")
    (testing "a day later the quiet networks are forgotten and room is made"
      (is (true? (sut/admit! "203.0.113.4" (+ (* 24 60 60 1000) 10)))))))


(deftest the-worker-is-called-at-most-a-hundred-times-a-day
  (with-server
   (fn [{:keys [ask db mail-goes? mails]}]
     (reset! mail-goes? false)
     (reset! @#'sut/mail-budget {:day (quot (System/currentTimeMillis) (* 24 60 60 1000)) :tried 99})
     (is (= 503 (:status (request-invite! ask stats))) "the hundredth mail is tried")
     (reset! mail-goes? true)
     (is (= 429 (:status (request-invite! ask stats))) "the next is not")
     (is (= 1 (count @mails)))
     (is (= [] (jdbc/execute! db ["SELECT * FROM invite_requests"]))))))


(deftest new-requests-stop-at-fifty-a-day-and-known-ones-still-answer
  (with-server
   (fn [{:keys [ask db mails]}]
     (request-invite! ask stats)
     (doseq [i (range 49)]
       (jdbc/execute! db
         ["INSERT INTO invite_requests (sha256, token_sha256, words, days) VALUES (?, ?, 0, 0)"
          (str "today-" i) (str "token-" i)]))
     (jdbc/execute! db
       ["INSERT INTO invite_requests (sha256, token_sha256, words, days, created_at)
         VALUES ('yesterday', 'token-y', 0, 0, UNIXEPOCH() - 90000)"])
     (is (= 429 (:status (request-invite! ask (assoc stats :sha256 (utils/sha256-hex "one-more")) "203.0.113.7"))))
     (is (= "pending" (answer (request-invite! ask stats "203.0.113.8"))) "a known hash still answers")
     (is (= 1 (count @mails))))))


(deftest a-first-request-waits-and-mails-the-operator-once
  (with-server
   (fn [{:keys [ask mails]}]
     (let [location (str "/api/invite-requests/" (:sha256 stats))
           created  (request-invite! ask stats)]
       (is (= [201 location "pending"]
              [(:status created) (get-in created [:headers :location]) (answer created)]))
       (is (= 1 (count @mails)))
       (is (str/includes? (:text (first @mails)) "Words: 120\nDays of practice: 14"))
       (testing "the same request again mails nothing and points at the request"
         (let [again (request-invite! ask stats)]
           (is (= [200 location "pending"]
                  [(:status again) (get-in again [:headers :location]) (answer again)])))
         (is (= 1 (count @mails))))))))


(deftest a-request-creates-no-couchdb-database-until-the-invite-is-redeemed
  (with-server
   (fn [{:keys [ask couch mails]}]
     (request-invite! ask stats)
     (ask :get (link (first @mails) "Grant"))
     (ask :post (link (first @mails) "Grant"))
     (status-of ask secret)
     (is (= [] @couch))
     (provision! ask secret)
     (is (seq @couch)))))


(deftest opening-a-link-changes-nothing
  (with-server
   (fn [{:keys [ask mails]}]
     (request-invite! ask stats)
     (let [page (:body (ask :get (link (first @mails) "Grant")))]
       (is (str/includes? page "120 words, 14 days of practice."))
       (is (str/includes? page ">Grant</button>")))
     (is (str/includes? (:body (ask :get (link (first @mails) "Decline"))) ">Decline</button>"))
     (is (= "pending" (status-of ask secret))))))


(deftest a-granted-secret-opens-one-account
  (with-server
   (fn [{:keys [ask mails]}]
     (request-invite! ask stats)
     (is (= 403 (provision! ask secret)) "before the grant the secret is no invite")
     (is (str/includes? (:body (ask :post (link (first @mails) "Grant"))) "Granted."))
     (is (= {:links {:provision "/api/identity/provision"} :status "granted"}
            (cheshire/parse-string (:body (read-request ask secret)) true))
         "the answer links the next step")
     (is (= [200 403] [(provision! ask secret) (provision! ask secret)]))
     (is (= "granted" (status-of ask secret)) "and the request stays granted"))))


(deftest a-request-is-decided-once
  (with-server
   (fn [{:keys [ask db mails]}]
     (let [other (assoc stats :sha256 (utils/sha256-hex "another-secret"))]
       (request-invite! ask stats)
       (request-invite! ask other)
       (let [[first-mail second-mail] @mails]
         (testing "declined, then granted"
           (ask :post (link first-mail "Decline"))
           (is (str/includes? (:body (ask :post (link first-mail "Grant"))) "Declined."))
           (is (= "declined" (status-of ask secret))))
         (testing "granted, then declined"
           (ask :post (link second-mail "Grant"))
           (is (str/includes? (:body (ask :post (link second-mail "Decline"))) "Granted."))
           (is (= "granted" (status-of ask "another-secret")))))
       (is (= [{:grants/sha256 (:sha256 other)}]
              (jdbc/execute! db ["SELECT sha256 FROM grants"])))))))


(deftest a-grant-that-expired-unused-is-asked-for-anew
  (with-server
   (fn [{:keys [ask db mails]}]
     (request-invite! ask stats)
     (let [old-grant (link (first @mails) "Grant")]
       (ask :post old-grant)
       (jdbc/execute! db ["UPDATE grants SET expires_at = 0"])
       (is (= "expired" (status-of ask secret)))
       (is (str/includes? (:body (ask :get old-grant)) "The invite expired unused."))
       (is (= 403 (provision! ask secret)))
       (testing "the same secret asks again and the operator gets new links"
         (is (= "pending" (answer (request-invite! ask stats))))
         (is (= 2 (count @mails)))
         (is (= 404 (:status (ask :get old-grant))))
         (ask :post (link (second @mails) "Grant"))
         (is (= 200 (provision! ask secret))))))))


(deftest a-failed-provision-gives-the-invite-back
  (with-server
   (fn [{:keys [ask couch-up? mails]}]
     (request-invite! ask stats)
     (ask :post (link (first @mails) "Grant"))
     (reset! couch-up? false)
     (is (= 500 (provision! ask secret)))
     (reset! couch-up? true)
     (is (= 200 (provision! ask secret))))))


(deftest a-link-nobody-was-mailed-answers-404
  (with-server
   (fn [{:keys [ask]}]
     (are [path] (= [404 404] [(:status (ask :get path)) (:status (ask :post path))])
      "/api/invite-requests/invented/grant"
      "/api/invite-requests/invented/decline"))))


(deftest a-request-shows-nothing-without-its-secret
  (with-server
   (fn [{:keys [ask]}]
     (is (= 404 (:status (read-request ask secret))) "nobody requested with it yet")
     (request-invite! ask stats)
     (are [presented] (= 404 (:status (read-request ask secret presented)))
      "another-secret"
      (:sha256 stats)
      nil))))


(deftest a-malformed-request-is-refused-and-mails-nothing
  (with-server
   (fn [{:keys [ask db mails]}]
     (are [body] (= 400 (:status (request-invite! ask body)))
      (dissoc stats :days)
      "not json")
     (is (= [] @mails))
     (is (= [] (jdbc/execute! db ["SELECT * FROM invite_requests"]))))))


(deftest a-request-whose-mail-fails-is-not-kept
  (with-server
   (fn [{:keys [ask db mail-goes? mails]}]
     (reset! mail-goes? false)
     (is (= 503 (:status (request-invite! ask stats))))
     (is (= [] (jdbc/execute! db ["SELECT * FROM invite_requests"])))
     (is (= 404 (status-of ask secret)))
     (testing "the next request mails again"
       (reset! mail-goes? true)
       (is (= "pending" (answer (request-invite! ask stats))))
       (is (= 2 (count @mails)))))))


(deftest a-sixth-request-from-one-address-in-a-day-is-refused
  (with-server
   (fn [{:keys [ask db mails]}]
     (let [request #(assoc stats :sha256 (utils/sha256-hex (str "secret-" %)))]
       (is (= [201 201 201 201 201 429]
              (mapv #(:status (request-invite! ask (request %))) (range 6))))
       (is (= 5 (count @mails)))
       (is (= 5 (count (jdbc/execute! db ["SELECT * FROM invite_requests"]))))
       (is (= 201 (:status (request-invite! ask (request 6) "203.0.113.2")))
           "another address is not held back")))))


(deftest the-mailer-posts-to-the-worker-with-its-secret
  (let [received (atom nil)
        answers  (atom [204 500])
        worker   (server/run-server (fn [request]
                                      (reset! received {:authorization (get-in request [:headers "authorization"])
                                                        :body (cheshire/parse-string (slurp (:body request)) true)})
                                      (let [[status] @answers]
                                        (swap! answers rest)
                                        {:status status}))
                                    {:legacy-return-value? false :port 0})
        mail!    (sut/mailer {"LEARNING_APP__INVITE_MAIL_SECRET" "s3cret"
                              "LEARNING_APP__INVITE_MAIL_URL"    (str "http://localhost:" (server/server-port worker))}
                             false)
        message  {:subject "Sprecha: invite request" :text "A device asks for an invite."}]
    (try
      (is (true? (mail! message)))
      (is (= {:authorization "Bearer s3cret" :body message} @received))
      (is (false? (mail! message)) "a failing Worker means the mail did not go")
      (finally
       @(server/server-stop! worker)))))


(deftest a-mailer-without-a-worker-sends-only-from-a-checkout
  (let [message {:subject "s" :text "t"}]
    (is (true? ((sut/mailer {} true) message)) "a checkout logs the mail")
    (is (false? ((sut/mailer {} false) message)) "a packaged app has nowhere to send it")))
