
(ns sagittariidae.fe.backend)

(def ^{:private true} db
  {:stage-details {"OQn6Q-p001-b001-c001-r001:Drn1Q-1"
                   [{:id 0 :file "scan-001.scn" :status :ready}
                    {:id 1 :file "scan-002.scn" :status :ready}
                    {:id 2 :file "scan-003.scn" :status :ready}]
                   "OQn6Q-p001-b001-c001-r001:bQ8bm-2"
                   [{:id 0 :file "strain.dat"   :status :processing}]}})

(defonce ^{:private true} sample-methods
  (:methods db))

(defn projects
  []
  (get db :projects))

(defn stage-methods
  []
  (get db :methods))

(defn stage-details
  [project-id sample-id stage-id]
  (get-in db [:stage-details (str sample-id ":" stage-id)]))
