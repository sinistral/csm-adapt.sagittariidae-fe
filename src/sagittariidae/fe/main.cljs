
(ns sagittariidae.fe.main
  (:require [cljs.pprint :refer [cl-format]]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as reagent]
            [sagittariidae.fe.backend :as be]
            [sagittariidae.fe.state :as state]
            ;; The following namespaces aren't explictly used, but must be
            ;; required to ensure that depdendent functionality (such as event
            ;; handlers) is made available.
            [sagittariidae.fe.event]
            [cljsjs.react-bootstrap]))

;; --------------------------------------------------------------- state --- ;;

(defn null-sample-state
  []
  {:id nil :stages [] :selected-stage nil})

(defn null-sample-stage-detail-state
  []
  {:stage-id nil :stage-details []})

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

(defn component:table
  [spec rows context]
  [:table.table.table-condensed.table-striped.table-hover
   ;; Note: Attempting to deref a Reagent atom inside a lazy seq can cause
   ;; problems, because the execution context could move from the component in
   ;; which lazy-deq is created, to the point at which it is expanded.  (In the
   ;; example below, the lazy-seq return by the `for` loop wouldn't be expanded
   ;; until the `:tr` is expanded, at which point the atom no longer knows that
   ;; the intent was to have it deref'd in this component.
   ;;
   ;; See [1] for a more detailed discussion of this issue.
   ;; [1] https://github.com/reagent-project/reagent/issues/18
   (list [:thead
          [:tr (doall
                (for [colkey (keys spec)]
                  [:th (get-in spec [colkey :label])]))]]
         [:tbody
          (for [row rows]
            [:tr (doall
                  (for [colkey (keys spec)]
                    [:td (let [data-fn (or (get-in spec [colkey :data-fn])
                                           identity)]
                           (data-fn (get row colkey) context))]))])])])

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
  [spec state]
  [component:table spec (:stages @state) (dissoc @state :id :stages)])

(defn component:sample-stage-details-table
  [spec state]
  [component:table spec (:stage-details @state)])

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

(defn add-component
  [c el]
  (reagent/render-component c (.getElementById js/document el)))

(defn main []
  (add-component [component:status-bar] "status-bar")

  (let [sample-state (reagent/atom (null-sample-state))
        sample-stage-detail-state (reagent/atom (null-sample-stage-detail-state))]
    (add-component [component:sample-search] "sample-search-bar")
    (add-component [component:project-dropdown] "nav-project-dropdown")
    (let [method-data-fn
          (fn [x]
            (:name (get (be/stage-methods) x)))
          id-btn-action
          (fn [sample-id stage-id]
            (.debug js/console (cl-format nil "Retrieving details of stage ~a for sample ~a" stage-id sample-id))
            ;; DANGER WILL ROBINSON: Non-atomic swap of multiple state
            ;; elements.  Is this an argument for storing all application state
            ;; in a single ref?
            (swap! sample-state #(assoc % :selected-stage stage-id))
            (reset! sample-stage-detail-state
                    {:stage-id stage-id
                     :stage-details (be/stage-details nil sample-id stage-id)}))
          id-data-fn
          (fn [stage-id context]
            (let [sample-id (:id @sample-state)]
              [(if (= (:selected-stage context) stage-id)
                 :button.btn.btn-success
                 :button.btn.btn-default)
               {:type "button"
                :on-click #(id-btn-action sample-id stage-id)}
               [:span.glyphicon.glyphicon-expand]]))
          spec
          {:method-id {:label "Method" :data-fn method-data-fn}
           :annotation {:label "Annotation"}
           :xref {:label "Cross reference"}
           :id {:label "" :data-fn id-data-fn}}]
      (add-component [component:sample-stage-table spec sample-state]
                     "sample-detail-table"))
    (let [spec
          {:file {:label "File"}
           :status {:label "Status" :data-fn str}}]
      (add-component [component:sample-stage-details-table spec sample-stage-detail-state]
                     "sample-stage-detail-table"))))
