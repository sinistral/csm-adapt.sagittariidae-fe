
(ns sagittariidae.fe.reagent-utils
  (:refer-clojure :exclude [key]))

(defn key
  [seq]
  (map-indexed #(with-meta %2 {:key %1}) seq))
