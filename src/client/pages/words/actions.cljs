(ns pages.words.actions
  (:require
   [clojure.string :as str]
   [domain.vocabulary :as domain]
   [nexus.registry :as nxr]
   [pages.words.edit :as edit]
   [ports.learner :as learner]
   [use-cases.collections :as collections-of]
   [use-cases.examples :as examples]
   [use-cases.vocabulary :as vocabulary]))


(def ^:private page-size
  "Rows the list grows by: a page, reached by entry and a new query one frame
   after they show `first-rows`, and one more each time the reader reaches the
   bottom."
  50)


(def ^:private first-rows
  "Rows shown in the task of the tap or the keystroke — as many as render
   within one frame with the rest of the screen, measured on a release build;
   the page follows on the next frame."
  20)


(defn content
  "The rows the word list shows, cut from the learner's data by the held query and row count:
   `:words/rows`, `:words/total` (words in scope before the query, which tells an empty vocabulary from a
   query with no match), `:words/more?` and `:words/in-collection?`."
  [state {:keys [active-collection learner now-ms]}]
  (let [{:keys [matches total words]}
        (vocabulary/rows learner
                         {:limit      (:words/limit state)
                          :search     (:words/search state)
                          :collection active-collection}
                         now-ms)]
    {:words/in-collection? (some? active-collection)
     :words/more? (< (count words) matches)
     :words/rows  words
     :words/total total}))


(defn- shown
  "`changes` to the list's query or row count, with the rows they cut."
  [state context changes]
  (let [state (merge state changes)]
    (merge changes (content state context))))


(nxr/register-action! :action/open-words
  ;; The screen and its first page in one write, in the task of the tap. It opens no dialog:
  ;; leaving with a word open would bring it back on the return.
  (fn open-words [state context]
    [[:effect/save
      (shown state
             context
             {:page/current  :page/words
              :words/editing nil
              :words/limit   first-rows
              :words/search  ""})]
     [:effect/after-paint [[:action/fill-page]]]]))


(nxr/register-action! :action/show-words
  ;; The rows computed again from memory at the held row count and query: after the load and the reader's own edit or removal.
  (fn show-words [state context]
    [[:effect/save (content state context)]]))


(defn- with-example
  "`editing` with `change` applied to the example of the collection `collection-id`."
  [editing collection-id change]
  (update-in editing [:examples collection-id] change))


(nxr/register-action! :action/open-word-edit
  ;; `:examples` start as the opened ones, which «Сбросить» brings back.
  (fn open-word-edit [_ {:keys [active-collection learner]} word-id]
    (when-let [{:keys [phrase? translations word]} (vocabulary/editable-word learner word-id)]
      (let [listing  (collections-of/listed learner)
            examples (learner/examples-of learner [word-id])
            ids      (edit/collection-ids word-id active-collection listing examples)
            opened   (edit/opened-examples ids examples)]
        [[:effect/save {:words/editing {:collection-ids      ids
                                        :collections         (mapv #(select-keys % [:id :name]) listing)
                                        :examples            opened
                                        :id                  word-id
                                        :opened-examples     opened
                                        :phrase?             phrase?
                                        :stored-translations translations
                                        :stored-word         word
                                        :translation-lines   translations
                                        :word                word}}]]))))


(nxr/register-action! :action/edit-translations
  ;; The text of the translations field, as its lines, blank ones kept: Enter makes a new line.
  (fn edit-translations [state text]
    [[:effect/save {:words/editing (assoc (:words/editing state) :translation-lines (str/split text #"\n" -1))}]]))


(nxr/register-action! :action/edit-example
  ;; The word-by-word breakdown of a generated sentence no longer describes it once it is typed over.
  (fn edit-example [state collection-id field text]
    [[:effect/save {:words/editing (with-example (:words/editing state)
                                                 collection-id
                                                 #(-> % (assoc field text) (dissoc :structure)))}]]))


(nxr/register-action! :action/reset-word-edit
  ;; Back to what the dialog opened with; it stays open.
  (fn reset-word-edit [state]
    (let [editing (:words/editing state)]
      [[:effect/abort-example-request]
       [:effect/save {:words/editing (assoc editing
                                            :example-request nil
                                            :examples (:opened-examples editing)
                                            :translation-lines (:stored-translations editing)
                                            :word (:stored-word editing)
                                            :word-taken? false)}]])))


(nxr/register-action! :action/regenerate-example
  ;; One request at a time; the sentence on the card goes along as the one not to repeat.
  (fn regenerate-example [state {:keys [learner]} collection-id]
    (let [{:keys [example-request examples id] :as editing} (:words/editing state)
          sentence (:value (get examples collection-id))]
      (when-not (= :request/pending (:status example-request))
        [[:effect/save {:words/editing (assoc editing
                                              :example-request {:collection-id collection-id
                                                                :status        :request/pending})}]
         [:effect/request-another-example
          {:avoid   (if (str/blank? sentence) [] [sentence])
           :subject (examples/subject {:collection-name (:name (learner/collection learner collection-id))
                                       :word            (learner/word learner id)})
           :word-id id}]]))))


(nxr/register-action! :action/example-arrived
  ;; A failure keeps the sentence on the card.
  (fn example-arrived [state word-id {:keys [example]}]
    (let [{:keys [example-request id] :as editing} (:words/editing state)]
      (when (and (= word-id id) (= :request/pending (:status example-request)))
        [[:effect/save {:words/editing (if example
                                         (-> editing
                                             (with-example (:collection-id example-request)
                                                           (constantly (select-keys example [:structure :translation :value])))
                                             (assoc :example-request nil))
                                         (assoc editing :example-request (assoc example-request :status :request/failed)))}]]))))


(nxr/register-action! :action/edit-spelling
  ;; The text typed into the dialog's word field, kept with whether it is another word's spelling.
  (fn edit-spelling [_ text]
    [[:effect/enter :action/show-spelling text]]))


(nxr/register-action! :action/show-spelling
  (fn show-spelling [state {:keys [learner]} text]
    (let [{:keys [id] :as editing} (:words/editing state)]
      [[:effect/save
        {:words/editing (assoc editing
                               :word text
                               :word-taken? (vocabulary/spelling-taken? learner id text))}]])))


(nxr/register-action! :action/close-word-edit
  (fn close-word-edit [_]
    [[:effect/abort-example-request]
     [:effect/save {:words/editing nil}]]))


(nxr/register-action! :action/search-words
  (fn search-words [_ search]
    [[:effect/enter :action/show-search search]]))


(nxr/register-action! :action/show-search
  ;; A new query starts at the first page and the list goes back to its top: the rows loaded for the old
  ;; query say nothing about how far down this one the reader has read.
  (fn show-search [state context search]
    [[:effect/save (shown state context {:words/limit first-rows :words/search search})]
     [:effect/scroll-words-to-top]
     [:effect/after-paint [[:action/fill-page]]]]))


(nxr/register-action! :action/fill-page
  ;; The rest of the first page, once the rows shown with the tap are painted, unless the reader left or it holds more.
  (fn fill-page [state]
    (when (and (= :page/words (:page/current state))
               (< (or (:words/limit state) 0) page-size))
      [[:effect/enter :action/show-page page-size]])))


(nxr/register-action! :action/show-more-words
  (fn show-more-words [state]
    (when (:words/more? state)
      [[:effect/enter :action/show-page (+ (:words/limit state) page-size)]])))


(nxr/register-action! :action/show-page
  (fn show-page [state context limit]
    [[:effect/save (shown state context {:words/limit limit})]]))


;; An edit or a removal goes to PouchDB, which memory follows; the list is computed again at the row
;; count on screen, so neither throws the reader back to the first page.
(nxr/register-action! :action/save-word
  ;; The dialog closes after the examples are written: reopened sooner, it would show the one that is going.
  (fn save-word [state {:keys [id translation value]}]
    (let [editing      (:words/editing state)
          renamed?     (domain/renamed? id value)
          ;; A renamed word is a new one: its examples are asked for again, not carried over.
          replacements (when-not renamed?
                         (for [{:keys [collection-id example]} (edit/changed-examples editing)]
                           {:collection-id collection-id
                            :example       (-> example (update :value str/trim) (update :translation str/trim))
                            :word          value
                            :word-id       id}))
          write        [:effect/write-word {:id id :renamed? renamed? :translation translation :value value}]]
      (when (edit/valid? editing)
        (if (seq replacements)
          [write [:effect/replace-example replacements]]
          [[:effect/abort-example-request] [:effect/save {:words/editing nil}] write])))))


(nxr/register-action! :action/remove-word
  (fn remove-word [_ word]
    [[:effect/delete-word word]]))
