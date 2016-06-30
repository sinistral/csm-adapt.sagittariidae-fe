
(ns sagittariidae.fe.main
  (:require [cljsjs.firebase]
            [cljsjs.react-bootstrap]
            [cljsjs.react-select]

            [clojure.string :as str]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [reagent.core :refer [adapt-react-class render]]
            [reagent.ratom :refer-macros [reaction]]
            [sagittariidae.fe.backend :as b]

            [sagittariidae.fe.event]
            [sagittariidae.fe.reagent-utils :as u]))

;; -------------------------------------------------- adapted components --- ;;

(defn react-bootstrap->reagent
  [c]
  (adapt-react-class (aget js/ReactBootstrap (str c))))

(def button        (react-bootstrap->reagent 'Button))
(def column        (react-bootstrap->reagent 'Col))
(def form-control  (react-bootstrap->reagent 'FormControl))
(def glyph-icon    (react-bootstrap->reagent 'Glyphicon))
(def grid          (react-bootstrap->reagent 'Grid))
(def menu-item     (react-bootstrap->reagent 'MenuItem))
(def nav-dropdown  (react-bootstrap->reagent 'NavDropdown))
(def progress-bar  (react-bootstrap->reagent 'ProgressBar))
(def row           (react-bootstrap->reagent 'Row))

(def select        (adapt-react-class js/Select))

;; ----------------------------------------------- composable components --- ;;

(defn- component:table
  "A generalised component for building rendering tables.  The table content is
  described by `spec`, a map of the form:
  ```
  {:column-name {:label \"RenderName\" :data-fn #(transform %)} ...}
  ```
  Note that while this is arguably an ugly hack, it is convenient for us to
  assume that our table spec is defined as a literal map (rather than being
  programmatically constructed) and that it contains a small number of
  columns (< 9).  Clojure(Script) optimises such maps to PersistentArrayMaps
  which preserve their insertion order."
  [spec rows]
  [:table.table.table-condensed.table-striped.table-hover
   {:style {:background-color "#fafafa"}}
   ;; Note: Attempting to deref a Reagent atom inside a lazy seq can cause
   ;; problems, because the execution context could move from the component in
   ;; which lazy-deq is created, to the point at which it is expanded.  (In the
   ;; example below, the lazy-seq return by the `for` loop wouldn't be expanded
   ;; until the `:tr` is expanded, at which point the atom no longer knows that
   ;; the intent was to have it deref'd in this component.
   ;;
   ;; See the following issue for a more detailed discussion of this issue:
   ;; https://github.com/reagent-project/reagent/issues/18
   (u/key
    (list
     [:thead
      [:tr (doall
            (for [colkey (keys spec)]
              (let [label (get-in spec [colkey :label])]
                ^{:key label}
                [:th label])))]]
     [:tbody
      (doall
       (for [row rows]
         (do
           (when-not (or (get row :id) (get row (:id-key spec)))
             (throw (ex-info "No `:id` and no ID key found for row data.  Row data must include an `:id` key, or the table spec must include an `:id-key` (that identifies the row column to use as the ID field); this is required to provide the required ReactJS `key` for dynamically generated children, and must provide an *identity* for the row, not just an index." row)))
           ^{:key (:id row)}
           [:tr (doall
                 (for [colkey (keys spec)]
                   (let [data-fn (or (get-in spec [colkey :data-fn]) (fn [x _] x))]
                     ^{:key colkey}
                     [:td (data-fn (get row colkey) row)])))])))]))])

(defn component:text-input-action
  [{:keys [placeholder value on-change on-click enabled?]
    :or   {enabled true}}]
  [:div.input-group
   [:input.form-control
    {:type        "text"
     :placeholder placeholder
     :value       value
     :on-change   #(on-change (-> % .-target .-value))
     :disabled    (not enabled?)}]
   [:span.input-group-btn
    [:button.btn.btn-default
     {:type       "button"
      :on-click   #(on-click)
      :disabled   (not enabled?)}
     [:span.glyphicon.glyphicon-download]]]])

(defn component:select
  [{:keys [options value enabled?]}]
  [select {:options   options
           :value     value
           :on-change #(dispatch [:event/stage-method-selected %])
           :disabled  (not enabled?)}])

;; ------------------------------------------------ top level components --- ;;

(defn component:sample-search
  []
  (let [enabled?  (subscribe [:query/ui-enabled?])
        sample-id (subscribe [:query/sample-id])]
    (fn []
      [component:text-input-action
       {:placeholder "Search for a sample ..."
        :value       (:name @sample-id)
        :on-change   #(dispatch [:event/sample-name-changed %])
        :on-click    #(dispatch [:event/sample-name-search-requested])
        :enabled?    @enabled?}])))

(defn component:sample-stage-detail-table
  []
  (let [sample-stage-detail (subscribe [:query/sample-stage-detail])]
    (fn []
      (let [spec {:file   {:label "File"}
                  :status {:label "Status" :data-fn (fn [x _] (name x))}}]
        [component:table spec (:file-spec @sample-stage-detail)]))))

(defn component:sample-stage-detail-upload-add-file-button
  [id]
  (let [enabled? (subscribe [:query/ui-enabled?])
        stages   (subscribe [:query/sample-stages])]
    (fn []
      [button {:id       id
               :title    "Select a file to upload."
               :disabled (or (not @enabled?) (nil? (:active @stages)))}
       [glyph-icon {:glyph "file"}]])))

(defn component:sample-stage-detail-upload-upload-file-button
  [res]
  (let [enabled? (subscribe [:query/ui-enabled?])
        stages   (subscribe [:query/sample-stages])]
    (fn []
      [button {:title    "Upload the selected file."
               :on-click #(.upload res)
               :disabled (or (not @enabled?) (nil? (:active @stages)))}
       [glyph-icon {:glyph "upload"}]])))

(defn component:sample-stage-detail-upload-form
  [btn-add btn-upl]
  (let [detail     (subscribe [:query/sample-stage-detail])
        filename   (reaction (if-let [f (get-in @detail [:upload :file])]
                               (.-fileName f)
                               ""))
        prog-n     (reaction (get-in @detail [:upload :progress]))
        prog-state (reaction (get-in @detail [:upload :state]))
        prog-style (reaction (cond (and (= @prog-state :success) (= @prog-n 1))
                                   :success
                                   (= @prog-state :error)
                                   :danger
                                   :else
                                   :default))]
    (fn []
      [:div
       [row
        [column {:md 12}
         [form-control {:type        "text"
                        :placeholder "Add file ..."
                        :value       @filename
                        :disabled    true}]]]
       [row {:style {:padding-top "10px"}}
        [column {:md 1}
         btn-add]
        [column {:md 1}
         btn-upl]
        [column {:md 10}
         [:div.progress
          {:id "stage-file-upload-progress"
           :style {:height "34px"}}
          [progress-bar (let [attrs {:id      "stage-file-upload-progress-bar"
                                     :striped (= :default @prog-style)
                                     :now     (* 100 @prog-n)
                                     :style   {:height "100%"}}]
                          (if (= :default @prog-style)
                            attrs
                            (assoc attrs :bs-style (name @prog-style))))]]]]])))

(defn component:sample-stage-input-form
  []
  (let [enabled?  (subscribe [:query/ui-enabled?])
        methods   (subscribe [:query/methods])
        options   (reaction (map #(-> %
                                      (assoc :value (:id %))
                                      (assoc :label (:name %1)))
                                 @methods))
        new-stage (subscribe [:query/sample-stage-input])]
    (fn []
      (let [{:keys [id method annotation]} @new-stage]
        [:div
         [row
          [column {:md 4}
           [component:select {:options  @options
                              :value    (:value method)
                              :enabled? @enabled?}]]
          [column {:md 8}
           [form-control {:type        "text"
                          :placeholder "Annotation ..."
                          :value       annotation
                          :on-change   #(dispatch [:event/stage-annotation-changed
                                                   (-> % .-target .-value)])
                          :disabled    (not @enabled?)}]]]
         [row {:style {:padding-top "10px"}}
          [column {:md 2}
           [button {:on-click (fn [_] (dispatch [:event/stage-added
                                                 id
                                                 (:id method)
                                                 annotation]))
                    :disabled (not @enabled?)}
            [glyph-icon {:glyph "plus"}]]]]]))))

(defn component:sample-stage-table
  []
  (let [test-methods  (subscribe [:query/methods])
        method-map    (reaction ;; Convert the list of maps into a map of maps,
                                ;; indexed by resource name.
                                (apply hash-map
                                       (mapcat (fn [x] [(:id x) x]) @test-methods)))
        sample-stages (subscribe [:query/sample-stages])]
    (fn []
      (let [mth-data-fn (fn [method _]
                          (:name (get @method-map method)))
            btn-data-fn (fn [_ {:keys [id]}]
                          [(if (= (:active @sample-stages) id)
                             :button.btn.btn-success
                             :button.btn.btn-default)
                           {:type     "button"
                            :on-click #(dispatch [:event/stage-selected id])}
                           [:span.glyphicon.glyphicon-chevron-right]])
            column-spec {:position   {:label "#"}
                         :method     {:label "Method" :data-fn mth-data-fn}
                         :annotation {:label "Annotation"}
                         :alt_id     {:label "Cross reference"}
                         :btn        {:label ""       :data-fn btn-data-fn}}]
        [component:table column-spec (map #(-> %1 (assoc :position %2))
                                          (:stages @sample-stages)
                                          (range))]))))

(defn component:project-dropdown
  []
  (let [user           (subscribe [:query/user])
        projects       (subscribe [:query/projects])
        active-project (subscribe [:query/active-project])
        project-name   (reaction (:name @active-project))]
    (fn []
      [nav-dropdown
       {:id       "nav-project-dropdown"
        :title    (let [prefix "Project"]
                    (if (or (nil? @project-name) (empty? @project-name))
                      prefix
                      (str prefix ": " @project-name)))
        :disabled (nil? @user)}
       (for [{:keys [id name] :as project} @projects]
         (let [event [:event/project-selected (select-keys project [:id :name])]]
           ^{:key id} [menu-item {:on-click #(dispatch event)} name]))])))

(defn component:user
  []
  (let [user (subscribe [:query/user])]
    (fn []
      (if (nil? @user)
        [nav-dropdown
         {:id "nav-user-dropdown"
          :title "User"}
         ^{:key "sign-in"} [menu-item
                            {:on-click #(dispatch [:event/user-signin-requested])}
                            "Sign in"]]
        [nav-dropdown
         {:id "nav-user-dropdown"
          :title (or (:display-name @user) (str (:user-id @user)))}
         ^{:key "sign-out"} [menu-item
                             {:on-click #(dispatch [:event/user-signout-requested])}
                             "Sign out"]]))))

;; --------------------------------------------------------- entry point --- ;;

(defn- by-id
  [el]
  (.getElementById js/document el))

(defn- add-component
  [c el]
  (render c (by-id el)))

(defn- make-resumable-js
  []
  (js/Resumable. #js {:target "http://localhost:5000/upload-part"

                      ;; The current version of Resumable doesn't support these
                      ;; options but they are available in mainline.  It would
                      ;; be nice to put them in place when they do become
                      ;; available, because it would clean up the API and make
                      ;; it less Resumable-specific.
                      ;;
                      ;; :currentChunkSizeParameterName "part-size"
                      ;; :chunkNumberParameterName      "part-number"
                      ;; :totalChunksParameterName      "total-parts"
                      ;; :totalSizeParameterName        "file-size"
                      ;; :identifierParameterName       "upload-id"
                      ;; :fileNameParameterName         "file-name"
                      ;; :typeParameterName             "file-type"

                      :testChunks                    false
                      :maxFiles                      1
                      :maxChunkRetries               9
                      :chunkRetryInterval            900

                      ;; Large uploads with many simultaneous connections tend
                      ;; to result in 'network connection was lost' errors.
                      ;; Various articles point the finger at Safari/MacOS
                      ;; (I've seen the same error in Chrome on MacOS) not
                      ;; handling Keep-Alive connections correctly.
                      ;;
                      ;; What I think is happening here though is that the
                      ;; server follow a strict request-response protocol.
                      ;; Thus the upload has to finish within the Keep-Alive
                      ;; timeout, since the server isn't streaming Keep-Alive
                      ;; data back to us while the upload is in progress.
                      :simultaneousUploads 1
                      :chunkSize           (* 128 1024)}))

(defn- make-firebase-app
  []
  (let [app-name "Sagittariidae"
        cached (first (filter #(= (.-name %) app-name) (.-apps js/firebase)))]
    (if cached
      [cached
       false]    ;; No initialisation required.
      [(-> js/firebase
           (.initializeApp #js {:apiKey     "AIzaSyCM1tgBP2c6AZwvzkxw8xzUuMPBFebjXjA"
                                :authDomain "sagittariidae-4e8fd.firebaseapp.com"}
                           app-name))
       true]))) ;; Initialisation required.

(defn- initialise-components
  [state]
  (add-component [component:user] "user")
  ;; Initialise the application state so that components have sensible defaults
  ;; for their first render.  Synchronous "dispatch" ensures that the
  ;; initialisation is complete before any of the components are created.
  (let [res (get-in state [:mutable :resumable])]
    (let [add-id "sample-stage-detail-upload-add-file-button"]
      (add-component [component:sample-stage-detail-upload-form
                      [component:sample-stage-detail-upload-add-file-button add-id]
                      [component:sample-stage-detail-upload-upload-file-button res]]
                     "sample-stage-detail-upload-form")
      (doto res
        ;; Component configuration
        (.assignBrowse (clj->js [(by-id add-id)]))
        ;; Callback configuration
        (.on "complete"
             (fn []
               (dispatch [:event/upload-file-parts-complete])))
        (.on "error"
             (fn [m f]
               (dispatch [:event/upload-file-error m f])))
        (.on "fileError"
             (fn [f m]
               (dispatch [:event/upload-file-error m f])))
        (.on "fileAdded"
             (fn [f]
               (dispatch [:event/upload-file-added f])))
        (.on "fileRetry"
             (fn [f]
               (.warn js/console "Resumable upload retry: " f)))
        (.on "progress"
             (fn []
               (dispatch [:event/upload-file-progress-updated (.progress res)])))
        (comment (.on "catchAll"
                      (fn [e & args]
                        (.debug js/console "resumableEvent: %s, %s" e args)))))))
  (add-component [component:project-dropdown]
                 "nav-project-dropdown")
  (add-component [component:sample-search]
                 "sample-search-bar")
  (add-component [component:sample-stage-table]
                 "sample-detail-table")
  (add-component [component:sample-stage-detail-table]
                 "sample-stage-detail-table")
  (add-component [component:sample-stage-input-form]
                 "sample-stage-input-form"))

(defn main
  []
  (let [resumable (make-resumable-js)
        [firebase init-firebase?] (make-firebase-app)]
    (dispatch-sync [:event/initialising
                    {:mutable {:resumable resumable
                               :firebase-app firebase}}])
    ;; Firebase nitialisation is deferred because it triggers event that update
    ;; the app state, so it can't be done until the application state itself
    ;; has been initialised.
    ;;
    ;; We could pre-initialise the app state with default that don't include
    ;; elements like Firebase that trigger state changes, but that means that
    ;; resorting to the less strict `maybe` checks.
    (if init-firebase?
      (let [evt #(dispatch [:event/user-authentication-changed %])]
        (-> firebase
            (.auth)
            (.onAuthStateChanged evt)))))
  (initialise-components @re-frame.db/app-db))
