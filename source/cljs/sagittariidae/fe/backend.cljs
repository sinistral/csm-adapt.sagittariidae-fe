
(ns sagittariidae.fe.backend)

(def ^{:private true} db
  {:samples {"P001-B001-C001-R001" [{:id 0 :method-id 0 :annotation "Ann0"}
                                    {:id 1 :method-id 1 :annotation "Ann1"}]
             "P001-B001-C001-R002" [{:id 0 :method-id 1 :annotation "Ann2"}]}
   :stage-details {"P001-B001-C001-R001:0" [{:id 0 :file "scan-001.scn" :status :ready}
                                            {:id 1 :file "scan-002.scn" :status :ready}
                                            {:id 2 :file "scan-003.scn" :status :ready}]
                   "P001-B001-C001-R001:1" [{:id 0 :file "strain.dat"   :status :processing}]}
   :methods [{:value 0 :label "X-ray tomography" :type :scan}
             {:value 1 :label "Compression"      :type :physical}
             {:value 2 :label "Strain"           :type :physical}
             {:value 3 :label "Porosity"         :type :analysis}]
   :projects {0 "Inconel"
              1 "Manhattan"
              2 "Van Buren"}})

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
