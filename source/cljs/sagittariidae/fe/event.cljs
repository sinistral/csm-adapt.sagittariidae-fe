
(ns sagittariidae.fe.event
  (:require [clojure.string :refer [trim]]
            [re-frame.core :refer [register-handler]]
            [sagittariidae.fe.backend :as b]
            [sagittariidae.fe.state :refer [null-state]]))

(register-handler
 :event/initialising
 (fn [state _]
   (if (empty? state)
     null-state
     state)))

(register-handler
 :event/project-selected
 (fn [state [_ id name]]
  (assoc null-state :project {:id id :name name})))

(register-handler
 :event/sample-id-changed
 (fn [state [_ sample-id]]
  (-> null-state
      (assoc :project (:project state))
      (assoc-in [:sample :id] sample-id))))

(register-handler
 :event/sample-id-search-requested
 (fn [state _]
  (assoc-in state [:sample :stages] (b/sample-stages nil (get-in state [:sample :id])))))

(register-handler
 :event/stage-selected
 (fn [state [_ stage-id]]
  (let [project-id (get-in state [:project :id])
        sample-id  (get-in state [:sample :id])]
    (-> state
        (assoc-in [:sample :active-stage :id]
                  stage-id)
        (assoc-in [:sample :active-stage :file-spec]
                  (b/stage-details project-id sample-id stage-id))))))

(register-handler
 :event/stage-method-selected
 (fn [state [_ m]]
  (assoc-in state [:sample :new-stage :method] (js->clj m :keywordize-keys true))))

(register-handler
 :event/stage-annotation-changed
 (fn [state [_ annotation]]
  (assoc-in state [:sample :new-stage :annotation] annotation)))

(register-handler
 :event/stage-added
 (fn [state _]
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
                    (get-in [:sample :new-stage] null-state)))))))
