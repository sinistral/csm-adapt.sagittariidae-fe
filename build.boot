
(defn- dependencies
  "Convert a leiningen-like dependency map into a boot dependency vector.  Map
  keys are the build stage, and values are vectors of the standard
  dependency [name version] tuple, e.g.:
  ```
  (set-env! :dependencies
            (dependencies {:build '[[org.clojure/clojure \"1.7.0\"] ...]
                           :test  '[[midje \"1.4.0\" :exclusions [org.clojure/clojure]]]
                           :dev   '[[org.slf5j/slf4j-nop \"1.7.13\"]]}))
  ```
  This example highlights another feature: build stage synonyms.  It can be
  (conceptually, if not practically) useful to distinguish between dependencies
  that provide development infrastrcture (REPL utils), and those that support
  testing (testing frameworks).  Thus `:dev` is a synonym for `:test` and both
  can be used together in the same definition make this distinction.  For
  convenience `:build` is a synonym for `compile`."
  [m]
  (letfn [(scope-dependency [scope spec]
            (let [[p v & opt-lst] spec
                  opts (apply hash-map opt-lst)]
              (vec (concat [p v] (reduce concat (assoc opts :scope scope))))))
          (scope-dependencies [scope specs]
            (vec (map #(scope-dependency scope %) specs)))]
    (vec
     (reduce concat
             (for [[scope specs] m]
               (scope-dependencies (cond (= :build scope) "compile"
                                         (= :dev scope) "test"
                                         :else (if (keyword? scope)
                                                 (name scope)
                                                 (str scope)))
                                   specs))))))

(set-env! :dependencies
          (dependencies {:build '[[org.clojure/clojure         "1.7.0"]
                                  [org.clojure/clojurescript   "1.7.228"]
                                  [cljs-ajax                   "0.5.5"]
                                  [prismatic/schema            "1.1.2"]
                                  [re-frame                    "0.7.0"]
                                  [reagent                     "0.6.0-alpha"]

                                  ;; CLJSJS-packaged JavaScript libraries.
                                  ;; These must be require'd by at least one
                                  ;; namespace for the CLJS build to include
                                  ;; them as build artefacts.
                                  [cljsjs/react-bootstrap      "0.29.2-0"]
                                  [cljsjs/react-select         "1.0.0-beta13-0"]]
                         :test  '[[crisptrutski/boot-cljs-test "0.2.2-SNAPSHOT"]]
                         :dev   '[;; build tasks
                                  [adzerk/boot-cljs            "1.7.228-1"]
                                  [adzerk/boot-cljs-repl       "0.3.0"]
                                  [adzerk/boot-reload          "0.4.8"]
                                  [degree9/boot-bower          "0.3.0"]
                                  [deraen/boot-less            "0.5.0"]  ;; [1]
                                  [org.slf4j/slf4j-nop         "1.7.13"]
                                  [pandeiro/boot-http          "0.7.3"]
                                  [sinistral/mantle            "0.2.1"]
                                  ;; CIDER nREPL
                                  [org.clojure/tools.nrepl     "0.2.12"]
                                  [com.cemerick/piggieback     "0.2.1"]
                                  [weasel                      "0.7.0"]]}))

;; [1] Required by cljsjs/react-bootstrap

(require
 '[clojure.java.io             :as    io]
 '[clojure.string              :as    str]
 '[boot.util                   :refer [info]]
 '[adzerk.boot-cljs            :refer [cljs main-files]]
 '[adzerk.boot-cljs-repl       :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload          :refer [reload]]
 '[crisptrutski.boot-cljs-test :refer [exit! test-cljs]]
 '[degree9.boot-bower          :refer [bower]]
 '[deraen.boot-less            :refer [less]]
 '[mantle.collection           :refer [single]]
 '[pandeiro.boot-http          :refer [serve]])

(defn p
  "Convert a '/' separated path into a one that uses the platform-specific
  separator.  This is a convenience function that allows paths to be written
  simply as literal strings using '/' separators."
  [s]
  (.getPath (apply io/file (str/split s #"/"))))

(set-env!
 :source-paths #{(p "source/cljs") "cljs-build-config"}
 :resource-paths (set (map p ["resource/html" "resource/css"])))

;; ---------------------------------------------------------- helper fns --- ;;

(def test-suffix "-test")

(defn inject-test-ns
  [reqs]
  (loop [original reqs
         injected reqs]
    (if (empty? original)
      injected
      (recur (rest original)
             (let [ns (first original)
                   test-ns (str ns test-suffix)]
               (if (contains? reqs test-ns)
                 injected
                 (conj injected (symbol test-ns))))))))

(defn add-test-ns-to-spec
  [new-fs-dir spec-edn]
  (let [in-file (tmp-file spec-edn)
        out-file (io/file new-fs-dir (tmp-path spec-edn))
        spec (read-string (slurp in-file))
        decorated (update-in spec [:require] inject-test-ns)]
    (info "Injecting test namespaces into %s: %s\n"
          (.getName in-file)
          (:require decorated))
    (spit out-file (pr-str decorated))))

;; --------------------------------------------------------------- tasks --- ;;

(def target-dir "target")

(deftask testing
  [i ids IDS #{str} "The IDs of the build to be tested"]
  (with-pre-wrap fileset
    (merge-env! :source-paths #{"test"})
    (if-let [build-spec-edns (seq (main-files fileset ids))]
      (let [tmp-dir (tmp-dir!)]
        (doall (map #(add-test-ns-to-spec tmp-dir %) build-spec-edns))
        (commit! (add-resource fileset tmp-dir)))
      fileset)))

(deftask install-js-dependencies
  []
  (let [js-deps {:bootstrap   "3.3.6"
                 :resumablejs "1.0.2"}]
    (comp (bower :install js-deps :directory ".")
          (sift  :include #{#"^.bowerrc$" #"^bower.json$"} :invert true))))

(deftask configure-rtenv
  [t env-tag TAG kw "A tag identifying the runtime context, typically \"prod\" or \"test\"."]
  (let [inject (fn [tag i o]
                 (doto o
                   (io/make-parents)
                   (spit (.replaceAll (slurp i) "__ENV__" (str tag)))))
        tmpdir (tmp-dir!)]
    (with-pre-wrap fs
      (empty-dir! tmpdir)
      (let [sfile (single (by-re [#"env\.cljs"] (input-files fs)))
            ifile (tmp-file sfile)
            fpath (tmp-path sfile)
            ofile (io/file tmpdir fpath)]
        (inject env-tag ifile ofile)
        (-> fs
            (rm [ifile])
            (add-source tmpdir)
            (commit!))))))

(deftask test
  []
  (comp (configure-rtenv)
        (testing)
        (test-cljs)
        (exit!)))

(deftask dev
  [i id ID str "The ID of the build for which REPL/auto-reload functionality is to be provided"]
  (comp (install-js-dependencies)
        (testing         :ids           #{id})
        (serve)
        (watch)
        (speak)
        (reload          :ids           #{id}
                         :on-jsload     (symbol (str "sagittariidae.fe." id) "main"))
        (cljs-repl       :ids           #{id})
        (configure-rtenv :env-tag       :test)
        (cljs            :ids           #{id}
                         :source-map    true
                         :optimizations :none)))

(deftask build
  []
  (comp (install-js-dependencies)
        (configure-rtenv :env-tag       :prod)
        (cljs            :ids           #{"main"}
                         :source-map    true
                         :optimizations :simple)
        (target          :directory     #{target-dir})))
