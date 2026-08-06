(ns db
  (:refer-clojure :exclude [find get remove use])
  #?(:cljs (:require-macros [db-macros :refer [with-couch-op]]))
  (:require
   #?(:clj [cheshire.core :as cheshire])
   #?(:clj [clojure.core :as clojure])
   #?(:clj [clojure.walk :as walk])
   #?(:clj [org.httpkit.client :as client])
   #?(:clj [promesa.core :as p])
   ;; PouchDB's browser bundle depends on the npm "events" package.
   #?(:cljs ["pouchdb" :as PouchDB])
   #?(:cljs ["pouchdb-find" :as PouchFind])
   [clojure.string :as str]))


#?(:cljs (.plugin PouchDB PouchFind))


(defn- kebab->snake [k] (str/replace (name k) #"-" "_"))


(def conn
  {:scheme   "http"
   :host     "localhost"
   :port     5984
   :username "admin"
   :password "3434"})


#?(:clj
   (defn- conn-base-url
     [conn]
     (str (:scheme conn) "://" (:host conn) ":" (:port conn))))


#?(:clj
   (defmacro raise
     [msg map]
     `(throw
       (ex-info ~msg ~map))))


(defn couch->clj
  "Recursively transforms CouchDB response into Clojure structure."
  [x]
  (let [keyfn (fn [k]
                (keyword
                 (let [k' (str/replace (name k) #"_" "-")]
                   (if (str/starts-with? (name k) "_")
                     (str \_ (subs k' 1))
                     k'))))
        f     (fn thisfn [x]
                #?(:clj (walk/prewalk
                         (fn [x]
                           (cond-> x
                             (map? x) (update-keys keyfn)))
                         x)
                   :cljs (cond
                           (seq? x)
                           (doall (map thisfn x))

                           (coll? x)
                           (into (empty x) (map thisfn x))

                           (array? x)
                           (vec (map thisfn x))

                           (identical? (type x) js/Object)
                           (into {}
                                 (for [k (js-keys x)]
                                   [(keyfn k) (thisfn (aget x k))]))

                           :else x)))]
    (f x)))


(defn clj->couch
  "Recursively transforms Clojure structure into CouchDB document."
  [x]
  (let [keyfn kebab->snake]
    #?(:clj (walk/prewalk
             (fn [x]
               (cond-> x
                 (map? x) (update-keys keyfn)))
             x)
       :cljs (when-not (nil? x)
               (cond
                 (keyword? x) (name x)
                 (symbol? x)  (str x)
                 (map? x)     (let [m (js-obj)]
                                (doseq [[k v] x]
                                  (aset m (keyfn k) (clj->couch v)))
                                m)
                 (coll? x)    (let [arr (array)]
                                (doseq [x (map clj->couch x)]
                                  (.push arr x))
                                arr)
                 :else        x)))))


(defn- not-found?
  [error]
  (let [data   #?(:clj (or (ex-data error) {})
                  :cljs error)
        status #?(:clj (:status data)
                  :cljs (.-status error))
        name   #?(:clj (:name data)
                  :cljs (.-name error))]
    (or (= status 404)
        (= status "404")
        (= name "not_found"))))


#?(:clj
   (defn- apply-conn
     [conn opts]
     (let [opts (dissoc opts :conn)
           opts (cond-> opts
                  (:body opts) (update :body cheshire/generate-string)
                  (:body opts) (update :headers #(merge {"Content-Type" "application/json"} (or % {})))
                  (contains? opts :query-params) (update :query-params update-keys clj->couch))]
       (-> opts
           (update :url #(str (conn-base-url conn) "/" %))
           (assoc :basic-auth [(:username conn) (:password conn)]
                  :as :text)))))


#?(:clj
   (defn request-sync
     "opts:
      - `url`
      - `method`
      - `headers`
      - `query-params`
      - `body`
      - `basic-auth`
      - `conn`"
     ([opts]
      (request-sync (or (:conn opts) conn) opts))
     ([conn opts]
      (let [response     @(client/request (apply-conn conn opts))
            headers      (:headers response)
            content-type (str (or (clojure/get headers "content-type")
                                  (:content-type headers)
                                  ""))]
        (cond-> response
          (and (string? (:body response))
               (str/includes? content-type "application/json"))
          (update :body cheshire/parse-string keyword))))))


#?(:clj
   (defn request
     "opts:
      - `url`
      - `method`
      - `headers`
      - `query-params`
      - `body`
      - `basic-auth`
      - `conn`"
     ([opts]
      (request (or (:conn opts) conn) opts))
     ([conn opts]
      (p/create
       (fn [resolve reject]
         (let [response (request-sync conn opts)]
           (if (-> response (:status 0) (< 400))
             (resolve (:body response))
             (reject (ex-info (str "CouchDB request error, status " (:status response)) response)))))))))


#?(:clj
   (defn exists?
     "Returns true if database exists.

      `dbname` (*string*) - name of the database"
     ([dbname]
      (exists? conn dbname))
     ([conn dbname]
      (let [response (request-sync conn {:method :head :url dbname})]
        (not= (:status response) 404)))))


#?(:clj
   (defn all-dbs
     "Returns the names of all databases on the server.

      https://docs.couchdb.org/en/stable/api/server/common.html#all-dbs"
     ([]
      (all-dbs conn))
     ([conn]
      (let [response (request-sync conn {:method :get :url "_all_dbs"})]
        (if (= (:status response) 200)
          (:body response)
          (raise "Could not list databases" (:body response)))))))


#?(:clj
   (defn db-updates
     "One longpoll turn of the server-wide updates feed: blocks until any
      database changes or `timeout-ms` passes, returns {:results [...] :last_seq s}.
      Requires the `_global_changes` database to exist.

      https://docs.couchdb.org/en/stable/api/server/common.html#db-updates"
     ([since timeout-ms]
      (db-updates conn since timeout-ms))
     ([conn since timeout-ms]
      (let [response (request-sync conn
                                   {:method :get
                                    :url    (str "_db_updates?feed=longpoll&since=" since
                                                 "&timeout=" timeout-ms)})]
        (if (= (:status response) 200)
          (:body response)
          (raise "Could not read _db_updates" (:body response)))))))


#?(:clj
   (defn create
     ([dbname]
      (create conn dbname))
     ([conn dbname]
      (let [response (request-sync conn {:method :put :url dbname})]
        (case (:status response)
          (201 202) (-> response :body :ok)
          (raise "DB was not created" (:body response)))))))


#?(:clj
   (defn use
     "Creates a database or opens an existing one.
      Returns a database instance.

     `dbname` (*string*) - name of the database "
     ([dbname]
      (use conn dbname))
     ([conn dbname]
      (when-not (exists? conn dbname)
        (create conn dbname))
      {:name dbname
       :conn conn})))


#?(:cljs
   (defn use
     "Creates a database or opens an existing one.
      Returns a database instance.

     `dbname` (*string*) - name of the database "
     [dbname]
     (PouchDB. dbname)))


#?(:clj
   (defn- db-conn
     [db]
     (or (:conn db) conn)))


#?(:clj
   (defmacro with-couch-op
     [_op-id body]
     `(p/-> ~body couch->clj)))


#?(:clj
   (defn secure
     "Returns the current security object from the specified database.

      Configuration:
      - `admins` (*object*) – Object with two fields as names and roles. See description below for more info.
      - `members` (*object*) – Object with two fields as names and roles. See description below for more info.

      - `names`: List of CouchDB user names
      - `roles`: List of users roles"
     [db cfg]
     (let [response (request-sync (db-conn db)
                                  {:url    (str (:name db) "/_security")
                                   :method :put
                                   :body   cfg})]
       (when (not= (:status response) 200)
         (raise "Security object was not set" (:body response))))))


(defn info
  "Get information about the database"
  [db]
  (with-couch-op :couch/info
    #?(:clj (request (db-conn db) {:method :get :url (:name db)})
       :cljs (.info db))))


(defn destroy
  "Delete the database"
  [db]
  (with-couch-op :couch/destroy
    #?(:clj (request (db-conn db) {:method :delete :url (:name db)})
       :cljs (.destroy ^js db))))


(defn insert
  "Create a new document or update an existing document.
   If the document already exists, you must specify its revision `_rev`, otherwise a conflict will occur.
   If `params` is a string, it's assumed it is the intended document `_id`.
   If `params` is an object, it's passed as query string parameters and `doc` is checked for defining the document `_id`
   If the `_id` field is not specified, a new unique ID will be generated, following whatever UUID algorithm is configured for that server.

   Params:
   - `rev` (*string*) – Document’s revision if updating an existing document. Alternative to `If-Match` header or document key. *Optional*
   - `batch` (*string*) – Stores document in [batch mode](https://docs.couchdb.org/en/stable/api/document/common.html#batch-mode-writes).
      Possible values: `ok`. *Optional*
   - `new-edits` (*boolean*) – Prevents insertion of a [conflicting document](https://docs.couchdb.org/en/stable/replication/conflicts.html).
      Possible values: `true` (default) and `false`.
      If `false`, a well-formed `_rev` must be included in the document.
     `new-edits=false` is used by the replicator to insert documents into the target database even if that leads to the creation of conflicts.
     *Optional. The `false` value is intended for use only by the replicator.*

   Returns:
   - `id` (*string*) - Document ID
   - `ok` (*boolean*) – Operation status
   - `rev` (*string*) – Revision info"
  ([db doc]
   (insert db doc {}))
  ([db doc params]
   (let [docid  (if (string? params) params (:_id doc))
         params (if (string? params) {} params)]
     (with-couch-op :couch/insert
       (if docid
         #?(:clj (request (db-conn db)
                          {:method       :put
                           :url          (str (:name db) "/" docid)
                           :body         (assoc doc :_id docid)
                           :query-params (clj->couch params)})
            :cljs (.put ^js db (clj->couch (assoc doc :_id docid)) (clj->couch params)))
         #?(:clj (request (db-conn db)
                          {:method :post :url (:name db) :body doc :query-params (clj->couch params)})
            :cljs (.post ^js db (clj->couch doc) (clj->couch params))))))))


(defn get
  "Gets a document whose `_id` is `docname`
   Params:
   - `attachments` (*boolean*) – Includes attachments bodies in response. Default is `false`
   - `att-encoding-info` (*boolean*) – Includes encoding information in attachment stubs if the particular attachment is compressed. Default is `false`.
   - `atts-since` (*array*) – Includes attachments only since specified revisions. Doesn’t include attachments for specified revisions. *Optional*
   - `conflicts` (*boolean*) – Includes information about conflicts in document. Default is `false`
   - `deleted-conflicts` (*boolean*) – Includes information about deleted conflicted revisions. Default is `false`
   - `latest` (*boolean*) – Forces retrieving latest 'leaf' revision, no matter what `rev` was requested. Default is `false`
   - `local-seq` (*boolean*) – Includes last update sequence for the document. Default is `false`
   - `meta` (*boolean*) – Acts same as specifying all `conflicts`, `deleted-conflicts`, and `revs-info` query parameters. Default is `false`
   - `open-revs` (*array*) – Retrieves documents of specified leaf revisions. Additionally, it accepts value as `all` to return all leaf revisions. *Optional*
   - `rev` (*string*) – Retrieves document of specified revision. *Optional*
   - `revs` (*boolean*) – Includes list of all known document revisions. Default is `false`
   - `revs-info` (*boolean*) – Includes detailed information for all known document revisions. Default is `false`.

   Returns:
   - `_id` (*string*) – Document ID.
   - `_rev` (*string*) – Revision MVCC token.
   - `_deleted` (*boolean*) – Deletion flag; present if the document was removed.
   - `_attachments` (*object*) – Attachment stubs; present if the document has attachments.
   - `_conflicts` (*array*) – List of conflicted revisions; present when `conflicts=true`.
   - `_deleted_conflicts` (*array*) – List of deleted conflicted revisions; present when `deleted_conflicts=true`.
   - `_local_seq` (*string*) – Document’s update sequence in the current database; present when `local_seq=true`.
   - `_revs_info` (*array*) – Objects with information about local revisions and their status; present when `open_revs` is used.
   - `_revisions` (*object*) – List of local revision tokens; present when `revs=true`. "
  ([db docname]
   (get db docname {}))
  ([db docname params]
   #?(:clj (p/catch
             (with-couch-op :couch/get
               (request (db-conn db)
                        {:method :get :url (str (:name db) "/" docname) :query-params (clj->couch params)}))
             (fn [error]
               (when-not (not-found? error)
                 (throw error))))
      :cljs (-> (with-couch-op :couch/get
                  (.get ^js db docname (clj->couch params)))
                (.catch (fn [error]
                          (when-not (not-found? error)
                            (throw error))))))))


(defn find
  "Find documents using a declarative JSON querying syntax.

   Query:
   - `selector` (*object*) – JSON object describing criteria used to select documents. More information provided in the section on selector syntax. Required
   - `limit` (*number*) – Maximum number of results returned. Default is 25. *Optional*
   - `skip` (*number*) – Skip the first 'n' results, where 'n' is the value specified. *Optional*
   - `sort` (*array*) – JSON array following sort syntax. *Optional*
   - `fields` (*array*) – JSON array specifying which fields of each object should be returned.
     If it is omitted, the entire object is returned. More information provided in the section on filtering fields. *Optional*
   - `use-index` (*string*|*array*) – Request a query to use a specific index.
     Specified either as `'<design_document>'`  or [`'<design_document>'`, `'<index_name>'`].
     It is not guaranteed that the index will be actually used because if the index is not valid for the selector, fallback to a valid index is attempted.
     Therefore that is more like a hint. When fallback occurs, the details are given in the `warning` field of the response. *Optional*
   - `allow-fallback` (*boolean*) – Tell if it is allowed to fall back to another valid index.
     This can happen on running a query with an index specified by `use-index` which is not deemed usable,
     or when only the built-in `_all_docs` index would be picked in lack of indexes available to support the query.
     Disabling this fallback logic causes the endpoint immediately return an error in such cases. Default is `true`. *Optional*
   - `conflicts` (*boolean*) – Include conflicted documents if true.
     Intended use is to easily find conflicted documents, without an index or view. Default is `false`. *Optional*
   - `r` (*number*) – Read quorum needed for the result.
     This defaults to 1, in which case the document found in the index is returned.
     If set to a higher value, each document is read from at least that many replicas before it is returned in the results.
     This is likely to take more time than using only the document stored locally with the index. *Optional*, default: 1
   - `bookmark` (*string*) – A string that enables you to specify which page of results you require. Used for paging through result sets.
     Every query returns an opaque string under the `bookmark` key that can then be passed back in a query to get the next page of results.
     If any part of the selector query changes between requests, the results are undefined. *Optional*, default: null
   - `update` (*boolean*) – Whether to update the index prior to returning the result. Default is `true`. *Optional*
   - `stable` (*boolean*) – Whether or not the view results should be returned from a “stable” set of shards. *Optional*
   - `stale` (*string*) – Combination of `update=false` and `stable=true` options. Possible options: `'ok'`, `false` (default). *Optional*.
     Note that this parameter is deprecated. Use `stable` and `update` instead. See Views Generation for more details.
   - `execution-stats` (*boolean*) – Include execution statistics in the query response. *Optional*, default: `false`

   Returns:
   - `docs` (*object*) – Array of documents matching the search.
      In each matching document, the fields specified in the fields part of the request body are listed, along with their values.
   - `warning` (*string*) – Execution warnings.
   - `execution-stats` (*object*) – Execution statistics.
   - `bookmark` (*string*) – An opaque string used for paging. See the bookmark field in the request (above) for usage details."
  [db query]
  #?(:clj (p/catch
            (with-couch-op :couch/find
              (request (db-conn db)
                       {:method :get :url (str (:name db) "/_find") :body (clj->couch query)}))
            (fn [error]
              (when-not (not-found? error)
                (throw error))))
     :cljs (-> (with-couch-op :couch/find
                 (.find ^js db (clj->couch query)))
               (.catch (fn [error]
                         (js/console.log :error error)
                         (when-not (not-found? error)
                           (throw error)))))))


(defn find-all
  "Find all documents matching a query using a single `find` call.

   It uses `info` to get total db `doc-count` and applies it as `:limit`.

   Returns the same shape as `find`: `{:docs [...]}`.

   Any incoming `:limit`/`:skip` in `query` is ignored."
  ([db query]
   (let [base-query (dissoc query :limit :skip)]
     #?(:clj (p/let [{:keys [doc-count]} (info db)]
               (if (zero? doc-count)
                 {:docs []}
                 (find db (assoc base-query :limit doc-count))))
        :cljs ((fn ^:async f []
                 (let [{:keys [doc-count]} (await (info db))]
                   (if (zero? doc-count)
                     {:docs []}
                     (await (find db (assoc base-query :limit doc-count)))))))))))


(defn create-index
  "Create a PouchDB secondary index.

  `fields` (*vector*) - fields for the index definition
  `opts` (*map*) - optional options passed to the index API"
  ([db fields]
   (create-index db fields {}))
  ([db fields opts]
   (let [fields (map kebab->snake fields)]
     (with-couch-op :couch/create-index
       #?(:clj (p/rejected "not implemented yet")
          :cljs (.createIndex ^js db (clj->couch (merge {:index {:fields fields}} opts))))))))


(defn remove
  "Marks the specified document as deleted by adding a field `_deleted` with the value true.

   Params:
   - `rev` (*string*) - Actual document's revision

   Returns:
   - `id` (*string*) – Document ID
   - `ok` (*boolean*) – Operation status
   - `rev` (*string*) – Revision token"
  ([db doc]
   (remove db doc nil {}))
  ([db doc params]
   (remove db doc nil params))
  ([db doc rev params]
   (let [rev (or (:_rev doc) (:rev params) rev)]
     (with-couch-op :couch/remove
       #?(:clj (request (db-conn db)
                        {:method :delete :url (str (:name db) "/" (:_id doc)) :query-params {:rev rev}})
          :cljs (.remove ^js db (clj->couch (assoc doc :_rev rev))))))))


(defn all-docs
  "Fetch multiple documents, indexed and sorted by the `_id`.
   Accepts an optional opts map: :startkey, :endkey, :limit, :include-docs."
  ([db]
   (all-docs db {}))
  ([db opts]
   (with-couch-op :couch/all-docs
     #?(:clj (request (db-conn db)
                      {:method :post
                       :url    (str (:name db) "/_all_docs")
                       :body   opts})
        :cljs (if (seq opts)
                (.allDocs ^js db (clj->couch opts))
                (.allDocs ^js db))))))


(defn bulk-docs
  "Create, update or delete multiple documents. The `docs` argument is an array of documents.

  If you omit an `_id` parameter on a given document, the database will create a new document and assign the ID for you.
  To update a document, you must include both an `_id` parameter and a `_rev` parameter,
  which should match the ID and revision of the document on which to base your updates.
  Finally, to delete a document, include a `_deleted` parameter with the value true."
  [db docs]
  (with-couch-op :couch/bulk-docs
    #?(:clj (request (db-conn db)
                     {:method :post
                      :url    (str (:name db) "/_bulk_docs")
                      :body   {:docs docs}})
       :cljs (.bulkDocs ^js db (clj->couch docs)))))


(defn bulk-delete
  "Delete each document `_id`/`_rev` combination within the submitted structure `docs` structures."
  [db docs]
  (let [docs (map #(assoc % :_deleted true) docs)]
    (bulk-docs db docs)))


(defn purge
  "Purges a specific revision of a document, specified by `doc-id` and `rev`. `rev` must be a leaf revision."
  [db doc-id rev]
  (with-couch-op :couch/purge
    #?(:clj (p/rejected "not implemented yet")
       :cljs (.purge ^js db doc-id rev))))


#?(:cljs
   (defn sync
     ([db]
      (sync db {}))
     ([db
       {:keys [remote-url live retry backoff-ms]
        :or   {live true retry true backoff-ms 60000}}]
      (let [dbname (.-name ^js db)
            remote (or remote-url
                       (str (.. js/globalThis -location -origin) "/db/" dbname))
            opts   #js {:live  live
                        :retry retry
                        :back_off_function
                        (fn [delay]
                          (let [next (if (zero? delay) 1000 (min backoff-ms (* 2 delay)))]
                            next))}]
        (PouchDB/sync dbname remote opts)))))


#?(:cljs
   (defn replicate-from
     ([db]
      (replicate-from db {}))
     ([db
       {:keys [remote-url live retry backoff-ms batch-size batches-limit checkpoint]
        :or   {live false retry true backoff-ms 60000}}]
      (let [dbname (.-name ^js db)
            remote (or remote-url
                       (str (.. js/globalThis -location -origin) "/db/" dbname))
            opts   (cond-> #js {:live  live
                                :retry retry
                                :back_off_function
                                (fn [delay]
                                  (if (zero? delay) 1000 (min backoff-ms (* 2 delay))))}
                     batch-size         (doto (aset "batch_size" batch-size))
                     batches-limit      (doto (aset "batches_limit" batches-limit))
                     (some? checkpoint) (doto (aset "checkpoint" checkpoint)))]
        (.replicate PouchDB remote dbname opts)))))
