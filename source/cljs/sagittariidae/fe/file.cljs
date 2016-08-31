
(ns sagittariidae.fe.file
  "In which are defined fuctions that wrap the Web File/Blob API to provide a
  more pleasing ClojureScript interface.")

(defn read-bytes
  "Read a portion of `file` and yield it as an `ArrayBuffer` to the function
  `f`. The read is done asynchronously and this function always returns `nil`."
  [f file start-byte end-byte]
  (doto (js/FileReader.)
    ((fn [r] (set! (.-onloadend r) f)))
    (.readAsArrayBuffer (.slice file start-byte end-byte)))
  nil)

(defn process-file-chunks
  [read-fn done-fn file chunks & {:keys [t] :or {t (.getTime (js/Date.))}}]
  (if (seq chunks)
    (let [chunk     (first chunks)
          onloadend #(if-not (nil? (-> % .-target .-error))
                       (.error js/console "Error reading file %s; %s" file (-> % .-target .-error))
                       (do
                         (read-fn chunk (-> % .-target .-result))
                         (process-file-chunks read-fn
                                              done-fn
                                              file
                                              (rest chunks)
                                              :t t)))]
      (read-bytes onloadend file (.-startByte chunk) (.-endByte chunk)))
    (let [duration (-> (js/Date.) .getTime (- t) (/ 1000))]
      (.debug js/console "Chunked processing of file %s completed in %ds"
              (.-name file) duration)
      (done-fn))))
