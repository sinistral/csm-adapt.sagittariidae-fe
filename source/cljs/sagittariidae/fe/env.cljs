
(ns sagittariidae.fe.env)

(def ^{:private true} env-tag
  "The value of this Var is injected at build time.  While convenient, it does
  have implications for dynamic redefs in a REPL; if the form, or file, is
  recompiled in a REPL, the value must be set manually."
  __ENV__)

(.debug js/console "SagittariidaeFE environment tag: %s" (clj->js env-tag))

(def ^{:private true :dynamic true} env-cfg
  ;; Var is dynamic only for testing purposes.
  {:service
   {:test {:host "http://localhost:5000"}}
   :upload
   {:test {:host "http://localhost:5000"}}})

(defn get-var [cfg-set cfg-key & {:keys [not-found]}]
  (when (nil? env-tag)
    (throw (ex-info "No environment tag has been set.  Please ensure that a value has been set in the build file, or set manually if the form or file has been redef'd in a REPL." {})))
  (get-in env-cfg [cfg-set env-tag cfg-key] not-found))
