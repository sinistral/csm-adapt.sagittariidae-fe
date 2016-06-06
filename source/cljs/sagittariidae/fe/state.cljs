
(ns sagittariidae.fe.state
  (:require [re-frame.core :refer [register-sub]]
            [reagent.core :as reagent]
            [reagent.ratom :refer-macros [reaction]]))

(defonce null-state
  {:project {:id nil
             :name nil}
   :sample {:id nil
            :stages []
            :active-stage {:id nil
                           :file-spec []}
            :new-stage {:method {}
                        :annotation ""}}})

(def state
  (reagent/atom null-state))

(defn query:project-id
  [state [query-id]]
  (assert (= query-id :query/project-id))
  (reaction (select-keys (:project @state) [:id :name])))
(register-sub :query/project-id query:project-id)

(defn query:sample-id
  [state [query-id]]
  (assert (= query-id :query/sample-id))
  (reaction (get-in @state [:sample :id])))
(register-sub :query/sample-id query:sample-id)

(defn query:sample-stages
  [state [query-id]]
  (assert (= query-id :query/sample-stages))
  (reaction {:stages (get-in @state [:sample :stages])
             :active (get-in @state [:sample :active-stage :id])}))
(register-sub :query/sample-stages query:sample-stages)

(defn query:sample-stage-detail
  [state [query-id]]
  (assert (= query-id :query/sample-stage-detail))
  (reaction (get-in @state [:sample :active-stage])))
(register-sub :query/sample-stage-detail query:sample-stage-detail)

(defn query:sample-stage-input
  [state [query-id]]
  (assert (= query-id :query/sample-stage-input))
  (reaction (get-in @state [:sample :new-stage])))
(register-sub :query/sample-stage-input query:sample-stage-input)
