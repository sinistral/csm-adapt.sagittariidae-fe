
(ns sagittariidae.fe.util)

(defn values-by-sorted-key
  ([m]
   (values-by-sorted-key m (sort (keys m))))
  ([m sorted-keys]
   (lazy-seq
    (when-let [ks (seq sorted-keys)]
      (cons (get m (first ks)) (values-by-sorted-key m (rest sorted-keys)))))))
