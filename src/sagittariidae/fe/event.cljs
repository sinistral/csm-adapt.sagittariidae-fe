
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
  (-> null-state
      (assoc :project (:project state))
      (assoc-in [:sample :id] sample-id)))
(register-handler :event/sample-id-changed handler:sample-id-changed)

(defn handler:sample-id-search-requested
  [state _]
  (assoc-in state [:sample :stages] (be/sample-stages nil (get-in state [:sample :id]))))
(register-handler :event/sample-id-search-requested handler:sample-id-search-requested)

(defn handler:stage-selected
  [state [_ stage-id]]
  (let [project-id (get-in state [:project :id])
        sample-id (get-in state [:sample :id])]
    (-> state
        (assoc-in [:sample :active-stage :id]
                  stage-id)
        (assoc-in [:sample :active-stage :file-spec]
                  (be/stage-details project-id sample-id stage-id)))))
(register-handler :event/stage-selected handler:stage-selected)
