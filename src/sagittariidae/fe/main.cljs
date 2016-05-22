
(ns sagittariidae.fe.main
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [cljsjs.react-bootstrap]))

;; --------------------------------------------------------------- state --- ;;

(def db {:samples {"P001-B001-C001-R001" [{:id 0 :method-id 0 :annotation "Ann0"}
                                          {:id 1 :method-id 1 :annotation "Ann1"}]
                   "P001-B001-C001-R002" [{:id 0 :method-id 1 :annotation "Ann2"}]}
         :methods {0 {:name "X-ray tomography" :type :scan}
                   1 {:name "Compression" :type :physical}
                   2 {:name "Strain" :type :physical}
                   3 {:name "Porosity" :type :analysis}}})

(defonce sample-methods
  (:methods db))

;; ----------------------------------------------- composable components --- ;;

(defn component:text-input-action
  [action placeholder]
  (let [state (reagent/atom nil)]
    [:div.input-group
     [:input.form-control {:type "text"
                           :placeholder placeholder
                           :on-change #(reset! state (-> % .-target .-value))}]
     [:span.input-group-btn
      [:button.btn.btn-default {:type "button"
                                :on-click #(action @state)}
       [:span.glyphicon.glyphicon-download]]]]))

(defn component:table
  [spec rows]
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
                           (data-fn (get row colkey)))]))])])])

;; ------------------------------------------------ top level components --- ;;

(defn component:status-bar
  []
  [:div [:p "Hello, " [:span {:style {:color "red"}} "World"]]])

(defn component:sample-search
  [target]
  (component:text-input-action (fn [s]
                                 (.debug js/console
                                         (str "Fetching details for sample " s))
                                 (reset! target {:id s :stages (or (get-in db [:samples s]) [])}))
                               "Sample ID"))

(defn component:sample-stage-table
  [spec state]
  [component:table spec (:stages @state)])

;; --------------------------------------------------------- entry point --- ;;

(defn add-component
  [c el]
  (reagent/render-component c (.getElementById js/document el)))

(defn main []
  (add-component [component:status-bar] "status-bar")

  (let [sample-state (reagent/atom {:id nil :stages []})]
    (add-component [component:sample-search sample-state] "sample-search-bar")
    (let [spec {:method-id
                {:label "Method"
                 :data-fn (fn [x]
                            (:name (get sample-methods x)))}
                :annotation
                {:label "Annotation"}
                :xref
                {:label "Cross reference"}
                :id
                {:label ""
                 :data-fn (fn [id]
                            (let [s-id (:id @sample-state)]
                              [:button.btn.btn-default {:type "button"
                                                        :on-click #(.debug js/console (cljs.pprint/cl-format nil "retrieving details of stage ~a for sample ~a" id s-id))}
                               [:span.glyphicon.glyphicon-expand]]))}}]
      (add-component [component:sample-stage-table spec sample-state] "sample-tests-table"))))
