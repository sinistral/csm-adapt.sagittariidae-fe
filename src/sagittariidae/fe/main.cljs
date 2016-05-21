
(ns sagittariidae.fe.main
  (:require [reagent.core :as reagent]
            [cljsjs.react-bootstrap]))

;; ----------------------------------------------- composable components --- ;;

(defn component:text-input-action
  [action placeholder]
  [:div.input-group
   [:input.form-control {:type "text" :placeholder placeholder}]
   [:span.input-group-btn
    [:button.btn.btn-default {:type "button" :on-click action}
     [:span.glyphicon.glyphicon-download]]]])

;; ------------------------------------------------ top level components --- ;;

(defn component:status-bar
  [properties]
  [:div [:p "Hello, " [:span {:style {:color "red"}} "World"]]])

(defn component:sample-search
  [properties]
  (component:text-input-action #(.debug js/console "Fetching samples...") "Sample ID"))

;; --------------------------------------------------------- entry point --- ;;

(defn main []
  (loop [components [[component:status-bar "status-bar"]
                     [component:sample-search "sample-search-bar"]]]
    (when (seq components)
      (let [[component element] (first components)]
        (reagent/render [component] (.getElementById js/document element)))
      (recur (rest components)))))
