
(ns sagittariidae.fe.event
  (:require [clojure.string :as s]
            [cljs.pprint :refer [cl-format]]
            [sagittariidae.fe.backend :as b]
            [ajax.core :refer [GET]]
            [re-frame.core :refer [dispatch register-handler]]
            [sagittariidae.fe.state :refer [clear copy-state null-state]]))

(def ^{:dynamic true} ajax-endpoint "http://localhost:5000")

(defn endpoint
  [rpath]
  (s/join "/" (concat [ajax-endpoint] rpath)))

(defn ajax-get
  [resource handler]
  (let [res-uri (endpoint resource)]
    (.info js/console "GET" res-uri)
    (GET res-uri
         :response-format :json
         :keywords?       :true
         :handler         handler)))

(defn urify
  [x]
  (-> x (s/trim) (js/encodeURIComponent)))

(register-handler
 :event/initialising
 (fn [state [_ res]]
   (ajax-get ["projects"] #(dispatch [:event/projects-retrieved %]))
   (ajax-get ["methods"] #(dispatch [:event/methods-retrieved %]))
   (-> (if (empty? state)
         null-state
         state)
       (assoc :resumable res))))

(register-handler
 :event/methods-retrieved
 (fn [state [_ methods]]
   (.info js/console "Received methods" (clj->js methods))
   (assoc-in state [:cached :methods] methods)))

(register-handler
 :event/projects-retrieved
 (fn [state [_ projects]]
   (.info js/console "Received projects" (clj->js projects))
   (assoc-in state [:cached :projects] projects)))

(register-handler
 :event/project-selected
 (fn [state [_ project]]
   (-> null-state
       (copy-state state [[:cached]])
       (assoc :project project))))

;; ---------------------------------------------------- sample ID search --- ;;

(register-handler
 :event/sample-name-changed
 (fn [state [_ sample-name]]
   (-> null-state
       (copy-state state [[:cached]
                          [:project]])
       (assoc-in [:sample :name] sample-name))))

(register-handler
 :event/sample-name-search-requested
 (fn [state _]
   (letfn []
     (ajax-get ["projects"
                (urify (get-in state [:project :id]))
                (str "samples?name=" (urify (get-in state [:sample :name])))]
               #(dispatch [:event/sample-retrieved %]))
     state)))

(register-handler
 :event/sample-retrieved
 (fn [state [_ sample]]
   (.info js/console "Retrieved sample details" (clj->js sample))
   (let [expected-id (get-in state [:sample :name])
         actual-id   (:name sample)]
     (if (= expected-id actual-id)
       (do
         (ajax-get ["projects"
                    (urify (get-in state [:project :id]))
                    "samples"
                    (urify (:id sample))
                    "stages"]
                   #(dispatch [:event/sample-stages-retrieved %]))
           (assoc-in state [:sample :id] (:id sample)))
       (do
         (.warn js/console "Details for sample %s are not for expected sample %s" actual-id expected-id)
         state)))))

(register-handler
 :event/sample-stages-retrieved
 (fn [state [_ rsp]]
   (.info js/console "Retrieved sample stages" (clj->js rsp))
   (when-let [stages (:stages rsp)]
     (let [expected-id (get-in state [:sample :id])
           actual-id   (:sample (first stages))]
       (if (= expected-id actual-id)
         (assoc-in state [:sample :stages] stages)
         (do (.warn js/console "Stages for sample %s are not for expected sample %s" actual-id expected-id)
             state))))))

;; ----------------------------------- sample stage, drilldown and input --- ;;

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
        method     (s/trim (or (get-in state [:sample :new-stage :method :label] "")))
        annotation (s/trim (or (get-in state [:sample :new-stage :annotation]) ""))]
    (when (and (not (empty? method)) (not (empty? annotation)))
      (-> state
          (assoc-in [:sample :stages]
                    (conj stages {:id         (+ 1 (:id (last stages)))
                                  :method     method
                                  :annotation annotation}))
          (copy-state null-state [[:sample :new-stage]]))))))

;; ------------------------------------------- sample stage detail input --- ;;

(def upload-path [:sample :active-stage :upload])

(register-handler
 :event/upload-file-complete
 (fn [state _]
   (.debug js/console "File upload complete.")
   (.removeFile (:resumable state) (get-in state (conj upload-path :file)))
   (let [new-state (assoc-in state (conj upload-path :state) :success)]
     (.debug js/console "state is now:" (clj->js new-state))
     new-state)))

(register-handler
 :event/upload-file-error
 (fn [state msg file]
   (.debug js/console "File upload error!")
   (assoc-in state (conj upload-path :state) :error)))

(register-handler
 :event/upload-file-added
 (.debug js/console "File added for upload.")
 (fn [state [_ f]]
   (.removeFile (:resumable state) (get-in state (conj upload-path :file)))
   (-> state
       (clear    upload-path)
       (assoc-in (conj upload-path :file) f))))

(register-handler
 :event/upload-file-progress-updated
 (.debug js/console "File upload progress update.")
 (fn [state [_ n]]
   (assoc-in state (conj upload-path :progress) n)))
