
(ns sagittariidae.fe.util
  (:require [clojure.string :as str]))

(defn ->id
  [s]
  (when s (first (str/split s #"-" 2))))

(defn pairs->map
  [pairs]
  (zipmap (map #(-> % first keyword) pairs) (map second pairs)))
