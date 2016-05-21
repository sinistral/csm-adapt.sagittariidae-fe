
(ns sagittariidae.fe.main
  (:require [clojure.string :as str]
            [reagent.core :as reagent]
            [cljsjs.react-bootstrap]))

;; --------------------------------------------------------------- state --- ;;

(def db {:samples {"P001-B001-C001-R001" [{:id 0 :annotation "Ann0"}
                                          {:id 1 :annotation "Ann1"}]
                   "P001-B001-C001-R002" [{:id 1 :annotation "Ann2"}]}
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
  [header rows]
  [:table.table.table-condensed.table-striped.table-hover
   (list [:thead
          [:tr (for [[_ label] header]
                 [:th label])]]
         [:tbody
          (for [row @rows]
            [:tr (for [[key _] header]
                   [:td (get row key)])])])])

;; ------------------------------------------------ top level components --- ;;

(defn component:status-bar
  []
  [:div [:p "Hello, " [:span {:style {:color "red"}} "World"]]])

(defn component:sample-search
  [target]
  (component:text-input-action (fn [s]
                                 (.debug js/console
                                         (str "Fetching details for sample " s))
                                 (reset! target (or (get-in db [:samples s]) [])))
                               "Sample ID"))

;; --------------------------------------------------------- entry point --- ;;

(defn add-component
  [c el]
  (reagent/render-component c (.getElementById js/document el)))

(defn main []
  (add-component [component:status-bar] "status-bar")

  (let [sample-stages (reagent/atom [])]
    (add-component [component:sample-search sample-stages] "sample-search-bar")
    (let [header {:id "#"
                  :method "Method"
                  :annotation "Annotation"
                  :xref "Cross reference"}]
      (add-component [component:table header sample-stages] "sample-tests-table"))))
