
(ns sagittariidae.fe.event
  (:require [clojure.string :refer [trim]]
            [re-frame.core :refer [register-handler]]
            [sagittariidae.fe.backend :as be]
            [sagittariidae.fe.state :refer [null-state]]))

(defn handler:initialising
  [state _]
  (if (empty? state)
    null-state
    state))
(register-handler :event/initialising handler:initialising)

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
        sample-id  (get-in state [:sample :id])]
    (-> state
        (assoc-in [:sample :active-stage :id]
                  stage-id)
        (assoc-in [:sample :active-stage :file-spec]
                  (be/stage-details project-id sample-id stage-id)))))
(register-handler :event/stage-selected handler:stage-selected)

(defn handler:stage-method-selected
  [state [_ m]]
  (assoc-in state [:sample :new-stage :method] (js->clj m :keywordize-keys true)))
(register-handler :event/stage-method-selected handler:stage-method-selected)

(defn handler:stage-annotation-changed
  [state [_ annotation]]
  (assoc-in state [:sample :new-stage :annotation] annotation))
(register-handler :event/stage-annotation-changed handler:stage-annotation-changed)

(defn handler:stage-added
  [state _]
  ;; FIXME: This MUST submit an update to the backend!
  (let [stages     (get-in state [:sample :stages])
        method-id  (get-in state [:sample :new-stage :method :value])
        method-ann (trim (or (get-in state [:sample :new-stage :annotation]) ""))]
    (when (and method-id (not (empty? method-ann)))
      (-> state
          (assoc-in [:sample :stages]
                    (conj stages {:id         (+ 1 (:id (last stages)))
                                  :method-id  method-id
                                  :annotation method-ann}))
          (assoc-in [:sample :new-stage]
                    (get-in [:sample :new-stage] null-state))))))
(register-handler :event/stage-added handler:stage-added)
