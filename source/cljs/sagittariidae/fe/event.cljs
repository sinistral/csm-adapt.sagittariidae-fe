
(ns sagittariidae.fe.event
  (:require [cljsjs.firebase]

            [clojure.string :as s]
            [cljs.pprint :refer [cl-format]]
            [sagittariidae.fe.backend :as b]
            [ajax.core :refer [GET POST PUT]]
            [re-frame.core :refer [dispatch register-handler]]
            [schema.core :refer [validate]]
            [sagittariidae.fe.state :refer [State
                                            authenticator
                                            clear copy-state
                                            null-state]]))

;; ------------------------------------------------------------- utility --- ;;

(defn- jsp->clj
  "Extract the properties from an arbitrary JavaScript object and return them
  as a ClojureScript map.  Keywordizing of property names is assumed."
  [o]
  (let [ps (mapcat #(let [k % v (goog.object/get o k)]
                      (when-not (fn? v)
                        [(keyword k) v]))
                   (js-keys o))]
    (apply hash-map ps)))

(defn- tr-keys
  "Translate the keys in a map; `tr-keys` is a map of old to new keys."
  [m tr-map]
  (loop [ks (keys tr-map)
         m  m]
    (if (seq ks)
      (recur (rest ks) (let [old-key (first ks)
                             new-key (get tr-map old-key)]
                         (-> m (assoc new-key (get m old-key)) (dissoc old-key))))
      m)))

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
 :event/initialising
 [validate-state]
 (fn [state [_ initial-state]]
   (ajax-get ["projects"] {:handler #(dispatch [:event/projects-retrieved %])})
   (ajax-get ["methods"] {:handler #(dispatch [:event/methods-retrieved %])})
   (-> (if (empty? state)
         null-state
         state)
       (conj initial-state))))

(register-handler
 :event/user-signin-requested
 [validate-state]
 (fn [state _]
   (-> (authenticator state) (.signInWithRedirect (js/firebase.auth.GoogleAuthProvider.)))
   state))

(register-handler
 :event/user-signout-requested
 [validate-state]
 (fn [state _]
   (-> (.signOut (authenticator state))
       (.then #(.debug js/console "User signed out")))
   (copy-state null-state state [[:mutable]])))

(register-handler
 :event/user-authentication-changed
 [validate-state]
 (fn [state [_ user]]
   (.debug js/console "User authentication changed: " user)
   (assoc state
          :user
          (if user
            (let [desired-keys [:user-id :display-name :provider-id]]
              (select-keys (tr-keys (jsp->clj user)
                                    (zipmap [:uid :displayName :providerId] desired-keys))
                           (conj desired-keys :email)))
            nil))))

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
       (copy-state state [[:user]
                          [:cached]
                          [:mutable]])
       (assoc :project project))))

;; ---------------------------------------------------- sample ID search --- ;;

(register-handler
 :event/sample-name-changed
 [validate-state]
 (fn [state [_ sample-name]]
   (-> null-state
       (copy-state state [[:user]
                          [:cached]
                          [:mutable]
                          [:project]])
       (assoc-in [:sample :name] sample-name))))

(register-handler
 :event/sample-name-search-requested
 [validate-state]
 (fn [state _]
   (ajax-get ["projects"
              (urify (get-in state [:project :id]))
              (str "samples?name=" (urify (get-in state [:sample :name])))]
             {:handler #(dispatch [:event/sample-retrieved %])})
   state))

(register-handler
 :event/sample-retrieved
 [validate-state]
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
                   {:handler #(dispatch [:event/sample-stages-retrieved %])})
         (assoc-in state [:sample :id] (:id sample)))
       (do
         (.warn js/console "Details for sample %s are not for expected sample %s" actual-id expected-id)
         state)))))

(register-handler
 :event/sample-stages-retrieved
 [validate-state]
 (fn [state [_ rsp]]
   (.info js/console "Retrieved sample stages" (clj->js rsp))
   (when-let [stages (:stages rsp)]
     (let [expected-id (get-in state [:sample :id])
           actual-id   (:sample (first stages))]
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
   (let [project-id (get-in state [:project :id])
         sample-id  (get-in state [:sample :id])]
     (-> state
         (assoc-in [:sample :active-stage :id]
                   stage-id)
         (assoc-in [:sample :active-stage :file-spec]
                   (b/stage-details project-id sample-id stage-id))))))

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
             sample-id  (get-in state [:sample :id])]
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
   (dispatch [:event/sample-name-search-requested])
   (copy-state state null-state [[:sample :new-stage]])))

;; ------------------------------------------- sample stage detail input --- ;;

(def upload-path [:sample :active-stage :upload])

(register-handler
 :event/upload-file-parts-complete
 (fn [state _]
   (.debug js/console "state @ part upload completion is:" (clj->js state))
   (let [f (get-in state (conj upload-path :file))]
     (ajax-post ["complete-multipart-upload"]
                {:upload-id (.-uniqueIdentifier f)
                 :file-name (.-fileName f)
                 :project   (:project state)
                 :sample    (get-in state [:sample :id])}
                {:handler   #(dispatch [:event/upload-file-complete])
                 :error-handler #(dispatch [:event/upload-file-error "File upload error" f])}))
   state))

(register-handler
 :event/upload-file-complete
 (fn [state _]
   (.debug js/console "file upload complete: " (get-in state (conj upload-path :file)))
   (.removeFile (get-in state [:mutable :resumable])
                (get-in state (conj upload-path :file)))
   (let [new-state (assoc-in state (conj upload-path :state) :success)]
     (.debug js/console "state @ file upload completion is:" (clj->js new-state))
     (assoc-in new-state (conj upload-path :progress) 1)
     new-state)))

(register-handler
 :event/upload-file-error
 (fn [state [_ msg file]]
   (.debug js/console "File upload error!: " msg)
   (.cancel (get-in state [:mutable :resumable]))
   (let [new-state (-> state
                       (assoc-in (conj upload-path :progress) 1)
                       (assoc-in (conj upload-path :state) :error))]
     (.debug js/console "state @ file upload error is:" (clj->js new-state))
     new-state)))

(register-handler
 :event/upload-file-added
 (.debug js/console "File added for upload.")
 (fn [state [_ f]]
   (let [new-state (-> state
                       (clear    upload-path)
                       (assoc-in (conj upload-path :file) f))]
     (.debug js/console "state is now:" (clj->js new-state))
     new-state)))

(register-handler
 :event/upload-file-progress-updated
 (fn [state [_ n]]
   ;;
   (if-not (= (get-in state (conj upload-path :state)) :error)
      (assoc-in state (conj upload-path :progress) n)
     state)))
