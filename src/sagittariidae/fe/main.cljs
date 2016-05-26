
(ns sagittariidae.fe.main
  (:require [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :refer [render-component]]
            [sagittariidae.fe.backend :as be]
            [sagittariidae.fe.state :as state]
            ;; The following namespaces aren't explictly used, but must be
            ;; required to ensure that depdendent functionality (such as event
            ;; handlers) is made available.
            [sagittariidae.fe.event]
            [cljsjs.react-bootstrap]))

;; ----------------------------------------------- composable components --- ;;

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
   ;; Note: Attempting to deref a Reagent atom inside a lazy seq can cause
   ;; problems, because the execution context could move from the component in
   ;; which lazy-deq is created, to the point at which it is expanded.  (In the
   ;; example below, the lazy-seq return by the `for` loop wouldn't be expanded
   ;; until the `:tr` is expanded, at which point the atom no longer knows that
   ;; the intent was to have it deref'd in this component.
   ;;
   ;; See the following issue for a more detailed discussion of this issue:
   ;; https://github.com/reagent-project/reagent/issues/18
   (list [:thead
          [:tr (doall
                (for [colkey (keys spec)]
                  [:th (get-in spec [colkey :label])]))]]
         [:tbody
          (for [row rows]
            [:tr (doall
                  (for [colkey (keys spec)]
                    (let [data-fn (or (get-in spec [colkey :data-fn])
                                      (fn [x _] x))]
                      [:td (data-fn (get row colkey) row)])))])])])

;; ------------------------------------------------ top level components --- ;;

(defn component:status-bar
  []
  [:div [:p "Hello, " [:span {:style {:color "red"}} "World"]]])

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

(defn component:sample-stage-table
  []
  (let [sample-stages (subscribe [:query/sample-stages])]
    (fn []
      (let [method-data-fn
            (fn [x]
              (:name (get (be/stage-methods) x)))
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

(defn component:sample-stage-detail-table
  []
  (let [sample-stage-detail (subscribe [:query/sample-stage-detail])]
    (fn []
      (let [spec {:file   {:label "File"}
                  :status {:label "Status" :data-fn (fn [x _] (name x))}}]
        [component:table spec (:file-spec @sample-stage-detail)]))))

(defn- make-project-dropdown
  [dropdown-btn dropdown-lst]
  ;; This is an ugly, ugly hack.  Eseentially this duplicates the layout and
  ;; styling already in the HTML.  Replacing just the structural elements (the
  ;; button and dropdown) has strange effects on the layout (seemingly because
  ;; of ReactJS insertions). Unfortunately, even when duplicating the layout,
  ;; the layout doesn't match the bare HTML, hence the need for explicit
  ;; padding ... Yuck!
  [:ul.nav.navbar-nav.navbar-right {:style {:padding-right "15px"}}
   [:li.dropdown
    [:a.dropdown-toggle {:data-toggle   "dropdown"
                         :role          "button"
                         :aria-haspopup "true"
                         :aria-expanded "false"
                         :href          "#"}
     dropdown-btn]
    [:ul.dropdown-menu
     dropdown-lst]]])

(defn component:project-dropdown
  []
  (let [project-id (subscribe [:query/project-id])]
    (fn []
      (make-project-dropdown
       (list [:span {:id    "project-dropdown-button-text"
                     :style {:padding-right "4px"}}
              (if (nil? (:id @project-id))
                "Project"
                (str "Project: " (:name @project-id)))]
              [:span.caret])
       (for [[id name] (be/projects)]
         [:li [:a {:href     "#"
                   :on-click #(dispatch [:event/project-selected id name])}
               name]])))))

;; --------------------------------------------------------- entry point --- ;;

(defn- add-component
  [c el]
  (render-component c (.getElementById js/document el)))

(defn main []
  (add-component [component:status-bar] "status-bar")
  (add-component [component:sample-search] "sample-search-bar")
  (add-component [component:project-dropdown] "nav-project-dropdown")
  (add-component [component:sample-stage-table] "sample-detail-table")
  (add-component [component:sample-stage-detail-table] "sample-stage-detail-table"))
