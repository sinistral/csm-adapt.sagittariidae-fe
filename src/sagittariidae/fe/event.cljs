
(ns sagittariidae.fe.event
  (:require [re-frame.core :refer [register-handler]]
            [sagittariidae.fe.backend :as be]
            [sagittariidae.fe.state :refer [null-state]]))

(defn handler:project-selected
  [state [_ id name]]
  (assoc null-state :project {:id id :name name}))
(register-handler :event/project-selected handler:project-selected)
