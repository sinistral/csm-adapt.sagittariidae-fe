
(ns sagittariidae.fe.main
  (:require [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [reagent.core :refer [adapt-react-class render]]
            [sagittariidae.fe.backend :as b]
            [sagittariidae.fe.reagent-utils :as u]
            ;; The following namespaces aren't explictly used, but must be
            ;; required to ensure that depdendent functionality (such as event
            ;; handlers) is made available.
            [sagittariidae.fe.event]
            [cljsjs.react-bootstrap]
            [cljsjs.react-select]))

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
  [placeholder value on-change on-click]
  [:div.input-group
   [:input.form-control
    {:type        "text"
     :placeholder placeholder
     :value       value
     :on-change   #(on-change (-> % .-target .-value))}]
   [:span.input-group-btn
    [:button.btn.btn-default
     {:type       "button"
      :on-click   #(on-click)}
     [:span.glyphicon.glyphicon-download]]]])

(defn component:select
  [v]
  [select {:options   (b/stage-methods)
           :value     v
           :on-change #(dispatch [:event/stage-method-selected %])}])

;; ------------------------------------------------ top level components --- ;;

(defn component:sample-search
  []
  (let [sample-id (subscribe [:query/sample-id])]
    (fn []
      (let [change #(dispatch [:event/sample-id-changed %])
            click #(dispatch [:event/sample-id-search-requested])]
        (component:text-input-action "Search for a sample ..."
                                     @sample-id
                                     change
                                     click)))))

(defn component:sample-stage-detail-table
  []
  (let [sample-stage-detail (subscribe [:query/sample-stage-detail])]
    (fn []
      (let [spec {:file   {:label "File"}
                  :status {:label "Status" :data-fn (fn [x _] (name x))}}]
        [component:table spec (:file-spec @sample-stage-detail)]))))

(defn component:sample-stage-input-form
  []
  (let [new-stage (subscribe [:query/sample-stage-input])]
    (fn []
      (let [{:keys [method annotation]} @new-stage]
        [:div
         [row
          [column {:md 4}
           [component:select (:value method)]]
          [column {:md 8}
           [form-control {:placeholder "Annotation ..."
                          :value       annotation
                          :type        "text"
                          :on-change   #(dispatch [:event/stage-annotation-changed (-> % .-target .-value)])}]]]
         [row {:style {:padding-top "10px"}}
          [column {:md 2}
           [button {:on-click #(dispatch [:event/stage-added])}
            [glyph-icon {:glyph "plus"}]]]]]))))

(defn component:sample-stage-table
  []
  (let [sample-stages (subscribe [:query/sample-stages])]
    (fn []
      (let [method-data-fn
            (fn [x]
              (:name (get (b/stage-methods) x)))
            btn-data-fn
            (fn [_ {:keys [id]}]
              [(if (= (:active @sample-stages) id)
                 :button.btn.btn-success
                 :button.btn.btn-default)
               {:type     "button"
                :on-click #(dispatch [:event/stage-selected id])}
               [:span.glyphicon.glyphicon-chevron-right]])
            spec
            {:id         {:label "#"}
             :method-id  {:label "Method"          :data-fn method-data-fn}
             :annotation {:label "Annotation"}
             :xref       {:label "Cross reference"}
             :btn        {:label ""                :data-fn btn-data-fn}}]
        [component:table spec (:stages @sample-stages)]))))

(defn component:project-dropdown
  []
  (let [project-id (subscribe [:query/project-id])]
    (fn []
      [nav-dropdown
       {:id "nav-project-dropdown"
        :title (if (nil? (:id @project-id))
                 "Project"
                 (str "Project: " (:name @project-id)))}
       (for [[id name] (b/projects)]
         (let [event [:event/project-selected id name]]
           ^{:key id} [menu-item {:on-click #(dispatch event)} name]))])))

;; --------------------------------------------------------- entry point --- ;;

(defn- add-component
  [c el]
  (render c (.getElementById js/document el)))

(defn main []
  ;; Initialise the application state so that components have sensible defaults
  ;; for their first render.  Synchronous "dispatch" ensures that the
  ;; initialisation is complete before any of the components are created.
  (dispatch-sync [:event/initialising])
  ;; "read-only" components
  (add-component [component:project-dropdown] "nav-project-dropdown")
  (add-component [component:sample-search] "sample-search-bar")
  (add-component [component:sample-stage-table] "sample-detail-table")
  (add-component [component:sample-stage-detail-table] "sample-stage-detail-table")
  ;; mutating components
  (add-component [component:sample-stage-input-form] "sample-stage-input-form"))
