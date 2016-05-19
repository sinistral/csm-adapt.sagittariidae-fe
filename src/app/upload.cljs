
(ns app.upload
  (:require [clojure.string :as str]
            [cljs.pprint :refer [cl-format]]))

(defn show
  [el]
  (let [vis (.-parentElement el)]
    (set! (.-className vis) (str/replace (.-className vis) #"\W*\bhide\b\W*" "")))
  el)

(defn hide
  [el]
  (let [vis (.-parentElement el)]
    (set! (.-className vis) (str (.-className vis) " hide")))
  el)

(defn set-progress
  [el pc]
  (set! (.-className el) (str/replace (.-className el) #"bar-(warning|error)" "bar-success"))
  (set! (.-width (.-style el)) (str pc "%")))

(defn set-progress-error
  [el msg]
  (set! (.-className el) (str/replace (.-className el) #"bar-success" "bar-error")))

(defn main []
  (let [c (.. js/document (createElement "DIV"))
        r (rand-int 99)]
    (aset c "innerHTML" (cl-format nil "<p>~a bottles of beer on the wall...</p>" r))
    (let [doc js/document
          res (js/Resumable. {:target "/upload" :testChunks true})]
      ;; Sanity checking CLJS reload
      (.. doc (getElementById "random") (appendChild c))
      ;; Set up Resumable
      (.assignBrowse res (.getElementById doc "add-file-btn"))
      (set! (.-onclick (.getElementById doc "start-upload-btn"))
            (fn []
              (.debug js/console "upload!")
              (.upload res)))
      (set! (.-onclick (.getElementById doc "pause-upload-btn"))
            (fn []
              (.debug js/console "pause!")
              (when (> (-> res (.-files) (.-length)) 0)
                (if (.isUploading res)
                  (.pause res)
                  (.upload res)))))
      (let [bar (.getElementById doc "upload-progress-bar")]
        (.on res "fileAdded"
             (fn [f]
               (.info js/console "uploading: " f)
               (show bar)
               (set-progress bar 0)))
        (.on res "fileSuccess"
             (fn [f]
               (.info js/console "upload success: " f)
               (hide bar)
               (set-progress bar 0)))
        (.on res "progress"
             (fn []
               (.info js/console "upload progress...")
               (set-progress bar (* (.progress res) 100))))
        (.on res "error"
             (fn [msg f]
               (.error js/console msg)
               (set-progress-error bar msg)))))))
