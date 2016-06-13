
(ns sagittariidae.fe.backend)

(def ^{:private true} db
  {:samples {"P001-B001-C001-R001" [{:id 0 :method "X-ray tomography" :annotation "Ann0"}
                                    {:id 1 :method "Strain" :annotation "Ann1"}]
             "P001-B001-C001-R002" [{:id 0 :method 5 :annotation "Ann2"}]}
   :stage-details {"P001-B001-C001-R001:0" [{:id 0 :file "scan-001.scn" :status :ready}
                                            {:id 1 :file "scan-002.scn" :status :ready}
                                            {:id 2 :file "scan-003.scn" :status :ready}]
                   "P001-B001-C001-R001:1" [{:id 0 :file "strain.dat"   :status :processing}]}})

(defonce ^{:private true} sample-methods
  (:methods db))

(defn projects
  []
  (get db :projects))

(defn sample-stages
  [project-id sample-id]
  (or (get-in db [:samples sample-id]) []))

(defn stage-methods
  []
  (get db :methods))

(defn stage-details
  [project-id sample-id stage-id]
  (get-in db [:stage-details (str sample-id ":" stage-id)]))
