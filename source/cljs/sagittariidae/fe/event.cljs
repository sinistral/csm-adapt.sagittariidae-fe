
(ns sagittariidae.fe.event
  (:require [clojure.string            :as    s]

            [ajax.core                 :refer [GET POST PUT]]
            [re-frame.core             :refer [dispatch register-handler]]
            [schema.core               :refer [validate]]
            [sagittariidae.fe.checksum :refer [array-buffer->utf8-array
                                               utf8-array->hex-string]]
            [sagittariidae.fe.file     :refer [process-file-chunks]]
            [sagittariidae.fe.state    :refer [State
                                               clear copy-state
                                               null-state]])
  (:import [goog.crypt Sha256]))

;; -------------------------------------------------------- server comms --- ;;

(def ^{:dynamic true} ajax-endpoint "http://localhost:5000")

(defn endpoint
  [rpath]
  (s/join "/" (concat [ajax-endpoint] rpath)))

(defn urify [x]
  (-> x (s/trim) (js/encodeURIComponent)))

(def ajax-default-params
  {:response-format :json
   :keywords?       :true
   :error-handler   (fn [e]
                      (.error js/console "AJAX error: " (clj->js e)))})

(def ajax-default-mutating-params
  (conj ajax-default-params
        {:format          :json
         :response-format :json
         :keywords?       :true}))

(defn ajax-get
  [resource params]
  (let [res-uri (endpoint resource)]
    (.info js/console "GET %s" res-uri)
    (apply GET
           (flatten (concat [res-uri]
                            (seq (-> ajax-default-params (conj params))))))))

(defn- ajax-mutate
  [action resource data params]
  (let [res-uri (endpoint resource)]
    (.info js/console (str action " " res-uri ";" data))
    (apply action
           (flatten (concat [res-uri]
                            (seq (-> ajax-default-mutating-params
                                     (conj params)
                                     (assoc :params data))))))))

(defn ajax-put
  [resource data params]
  (ajax-mutate PUT resource data params))

(defn ajax-post
  [resource data params]
  (ajax-mutate POST resource data params))

;; ---------------------------------------------------------- middleware --- ;;

(defn validate-state
  [handler]
  (fn [state event-vectr]
    (validate State (handler state event-vectr))))

;; ---------------------------------------------------------------- init --- ;;

(register-handler
 :event/initializing
 [validate-state]
 (fn [state [_ initial-state]]
   (ajax-get ["projects"] {:handler #(dispatch [:event/projects-retrieved %])})
   (ajax-get ["methods"] {:handler #(dispatch [:event/methods-retrieved %])})
   (-> (if (empty? state)
         null-state
         state)
       (conj initial-state))))

;; --- static data --------------------------------------------------------- ;;

(register-handler
 :event/methods-retrieved
 [validate-state]
 (fn [state [_ methods]]
   (.info js/console "Received methods" (clj->js methods))
   (assoc-in state [:cached :methods] methods)))

(register-handler
 :event/projects-retrieved
 [validate-state]
 (fn [state [_ projects]]
   (.info js/console "Received projects" (clj->js projects))
   (assoc-in state [:cached :projects] projects)))

(register-handler
 :event/project-selected
 [validate-state]
 (fn [state [_ project]]
   (-> null-state
       (copy-state state [[:cached]
                          [:volatile]])
       (assoc :project project))))

;; ------------------------------------------------------- sample search --- ;;

(register-handler
 :event/sample-search-terms-changed
 [validate-state]
 (fn [state [_ sample-name]]
   (-> null-state
       (copy-state state [[:cached]
                          [:volatile]
                          [:project]])
       (assoc :search-terms sample-name))))

(register-handler
 :event/sample-search-requested
 [validate-state]
 (fn [state _]
   (ajax-get ["projects"
              (urify (get-in state [:project :id]))
              (str "samples?q=" (urify (get state :search-terms)))]
             {:handler #(dispatch [:event/samples-retrieved %])})
   state))

(register-handler
 :event/samples-retrieved
 [validate-state]
 (fn [state [_ samples]]
   (.info js/console "Search returned %d samples" (count samples))
   (assoc state :search-results (map #(select-keys % [:id :name]) samples))))

(register-handler
 :event/sample-selected
 [validate-state]
 (fn [state [_ sample]]
   (.info js/console "Retrieving sample details" (clj->js sample))
   (ajax-get ["projects"
              (urify (get-in state [:project :id]))
              "samples"
              (urify (:id sample))
              "stages"]
             {:handler #(dispatch [:event/sample-stages-retrieved %])})
   (assoc-in state [:sample :selected] sample)))

(register-handler
 :event/sample-stages-retrieved
 [validate-state]
 (fn [state [_ rsp]]
   (.info js/console "Retrieved stages for sample"
          (clj->js (get-in state [:sample :selected]))
          (clj->js rsp))
   (.debug js/console "Expecting stages for sample: %s" (get-in state [:sample :selected :id]))
   (when-let [stages (:stages rsp)]
     (let [expected-id (get-in state [:sample :selected :id])
           actual-id   (:sample rsp)]
       (if (= expected-id actual-id)
         (-> state
             (assoc-in [:sample :stages :list] stages)
             (assoc-in [:sample :stages :token] (:token rsp)))
         (do (.warn js/console "Stages for sample %s are not for expected sample %s" actual-id expected-id)
             state))))))

;; ----------------------------------- sample stage, drilldown and input --- ;;

(register-handler
 :event/stage-selected
 [validate-state]
 (fn [state [_ stage-id]]
   (ajax-get ["projects"
              (urify (get-in state [:project :id]))
              "samples"
              (urify (get-in state [:sample :selected :id]))
              "stages"
              (urify stage-id)
              "files"]
             {:handler #(dispatch [:event/stage-details-retrieved %])})
   (assoc-in state [:sample :active-stage :id] stage-id)))

(register-handler
 :event/refresh-sample-stage-details
 [validate-state]
 (fn [state _]
   (dispatch [:event/stage-selected (get-in state [:sample :active-stage :id])])
   state))

(register-handler
 :event/stage-details-retrieved
 [validate-state]
 (fn [state [_ stage-details]]
   (.info js/console "Retrieved stage details %s" (clj->js (str stage-details)))
   (let [exp-id (get-in state [:sample :active-stage :id])
         act-id (:stage-id stage-details)]
     (if (= exp-id act-id)
       (let [fmtfn #(assoc % :status (or (#{:processing :ready} (keyword (:status %))) :unknown))
             files (map fmtfn (:files stage-details))]
         (when (some #{:processing} (map :status files))
           (do (.debug js/console "Incomplete stage file detected ... scheduling refresh")
               (.setTimeout js/window #(dispatch [:event/refresh-sample-stage-details]) (* 15 1000))))
         (assoc-in state [:sample :active-stage :file-spec] files))
       (do (.info js/console "Actual stage ID '%s' does not match expected stage ID '%s'; ignoring stage detail update"
                  act-id exp-id)
           state)))))

(register-handler
 :event/stage-method-selected
 [validate-state]
 (fn [state [_ m]]
   (assoc-in state [:sample :new-stage :method] (js->clj m :keywordize-keys true))))

(register-handler
 :event/stage-annotation-changed
 [validate-state]
 (fn [state [_ annotation]]
   (assoc-in state [:sample :new-stage :annotation] annotation)))

(register-handler
 :event/stage-added
 [validate-state]
 (fn [state [_ i m a]]
   (let [method     (s/trim (or m ""))
         annotation (s/trim (or a ""))]
     (when (and (not (empty? method)) (not (empty? annotation)))
       (let [project-id (get-in state [:project :id])
             sample-id  (get-in state [:sample :selected :id])]
         (.info js/console
                "Adding stage for sample %s,%s: method=%s, annotation=%s"
                project-id sample-id method annotation)
         (ajax-put ["projects" (urify project-id)
                    "samples"  (urify sample-id)
                    "stages"   i]
                   {:method     method
                    :annotation annotation}
                   {:handler    #(dispatch [:event/stage-persisted %])}))))
   state))

(register-handler
 :event/stage-persisted
 [validate-state]
 (fn [state [_ new-stage]]
   (dispatch [:event/sample-selected (get-in state [:sample :selected])])
   (copy-state state null-state [[:sample :new-stage]])))

;; ------------------------------------------- sample stage detail input --- ;;

(def upload-path [:sample :active-stage :upload])

(register-handler
 :task/chunking-start
 [validate-state]
 (fn [state _]
   (-> state
       (copy-state null-state [upload-path])
       (assoc-in [:volatile :digester] (Sha256.)))))

(register-handler
 :task/preprocess-chunks
 [validate-state]
 (fn [state [_ chunks]]
   (let [digester (get-in state [:volatile :digester])
         checksum (fn [chunk buf]
                    (let [len (count chunks)
                          cur (+ (.-offset chunk) 1)]
                      (when (or (= (rem cur 500) 0) (= cur len))
                        (.debug js/console "Processing chunk %d of %d" cur len))
                      (.update digester (array-buffer->utf8-array buf))
                      (dispatch [:event/upload-file-chunk-digested cur len])))
         complete (fn []
                    (let [hex (utf8-array->hex-string (.digest digester))]
                      (dispatch [:event/upload-file-checksum-computed hex])))]
     (process-file-chunks
      checksum complete (-> (first chunks) .-fileObj .-file) chunks))
   state))

(register-handler
 :event/upload-file-chunk-digested
 [validate-state]
 (fn [state [_ n cnt]]
   (assoc-in state (conj upload-path :checksum :progress) (/ n cnt))))

(register-handler
 :event/upload-file-checksum-computed
 [validate-state]
 (fn [state [_ checksum]]
   (.info js/console "Generated checksum" checksum)
   (-> state
       (assoc-in (conj upload-path :checksum :value) checksum)
       (assoc-in (conj upload-path :checksum :state) :success)
       (assoc-in (conj upload-path :checksum :progress) 1))))

(register-handler
 :event/upload-file-parts-complete
 [validate-state]
 (fn [state _]
   (.debug js/console "state @ part upload completion is:" (clj->js state))
   (let [f (get-in state (conj upload-path :file))]
     (ajax-post ["complete-multipart-upload"]
                {:upload-id       (.-uniqueIdentifier f)
                 :file-name       (.-fileName f)
                 :project         (:project state)
                 :sample          (get-in state [:sample :selected :id])
                 :sample-stage    (get-in state [:sample :active-stage :id])
                 :checksum-method :sha256
                 :checksum-value  (-> state (get-in (conj upload-path :checksum :value)))}
                {:handler         #(dispatch [:event/upload-file-complete])
                 :error-handler   #(dispatch [:event/upload-file-error "File upload error" f])}))
   (-> state
       (copy-state null-state [[:volatile :digester]]))))

(register-handler
 :event/upload-file-complete
 [validate-state]
 (fn [state _]
   (.debug js/console "file upload complete: " (get-in state (conj upload-path :file)))
   (.removeFile (get-in state [:volatile :resumable])
                (get-in state (conj upload-path :file)))
   (dispatch [:event/refresh-sample-stage-details])
   (let [new-state (assoc-in state (conj upload-path :transmit :state) :success)]
     (.debug js/console "state @ file upload completion is:" (clj->js new-state))
     (assoc-in new-state (conj upload-path :transmit :progress) 1)
     new-state)))

(register-handler
 :event/upload-file-error
 [validate-state]
 (fn [state [_ msg file]]
   (.debug js/console "File upload error!: " msg)
   (.cancel (get-in state [:volatile :resumable]))
   (let [new-state (-> state
                       (assoc-in (conj upload-path :transmit :progress) 1)
                       (assoc-in (conj upload-path :transmit :state) :error))]
     (.debug js/console "state @ file upload error is:" (clj->js new-state))
     new-state)))

(register-handler
 :event/upload-file-added
 [validate-state]
 (fn [state [_ f]]
   (.debug js/console "File added for upload.")
   (let [new-state (-> state
                       (clear    upload-path)
                       (assoc-in (conj upload-path :file) f))]
     (.debug js/console "state is now:" (clj->js new-state))
     new-state)))

(register-handler
 :event/upload-file-progress-updated
 [validate-state]
 (fn [state [_ n]]
   (if-not (= (get-in state (conj upload-path :transmit :state)) :error)
      (assoc-in state (conj upload-path :transmit :progress) n)
     state)))
