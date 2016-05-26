
(ns sagittariidae.fe.event
  (:require [re-frame.core :refer [register-handler]]
            [sagittariidae.fe.backend :as be]
            [sagittariidae.fe.state :refer [null-state]]))

(defn handler:project-selected
  [state [_ id name]]
  (assoc null-state :project {:id id :name name}))
(register-handler :event/project-selected handler:project-selected)

(defn handler:sample-id-changed
  [state [_ sample-id]]
  (.debug js/console "sample id changed: " sample-id)
  (assoc-in state [:sample :id] sample-id))
(register-handler :event/sample-id-changed handler:sample-id-changed)

(defn handler:sample-id-search-requested
  [state _]
  (.debug js/console "search requested for: " (get-in state [:sample :id]))
  (assoc-in state [:sample :stages] (be/sample-stages nil (get-in state [:sample :id]))))
(register-handler :event/sample-id-search-requested handler:sample-id-search-requested)
