
(ns sagittariidae.fe.state
  (:require [re-frame.core :refer [register-sub]]
            [reagent.core :as reagent]
            [reagent.ratom :refer-macros [reaction]]
            [schema.core :refer [Int Keyword Num Str
                                 enum maybe]]))

(def State
  "The Prismatic Schema for the Sagittariidae application state."
  (let [method {:id Str
                :name Str
                :description Str}]
    {:project {:id Str
               :name Str}
     :cached {:projects [{:id Str
                          :name Str
                          :sample-mask Str}]
              :methods [method]}
     :search-terms Str
     :search-results [{:id Str
                       :name Str}]
     :sample {:selected {:id Str
                         :name Str}
              :stages {:list  [{:id Str
                                :method Str
                                :annotation Str
                                :alt-id (maybe Str)
                                :sample Str}]
                       :token (maybe Str)}
              :active-stage {:id (maybe Str)
                             :file-spec [{:id Str
                                          :file Str
                                          :mtime Str
                                          :status (enum :unknown :processing :ready)}]
                             :upload {:file (maybe js/Object) ;; ResumableFile [1]
                                      :progress Num
                                      :state Keyword}}
              :new-stage {:method (maybe (conj method {:label Str
                                                       :value Str}))
                          :annotation Str}}
     :mutable {:resumable js/Resumable}}))

;; [1] These should all be specific types of JavaScript objects.  Schema
;;     requires JS prototype functions to do type matching, and whatever these
;;     are don't fit the bill.  I need to understand the JS prototyping model
;;     better and try to figure this out.

(def null-state
  {:cached {:projects []
            :methods []}
   :project {:id ""
             :name ""}
   :search-terms ""
   :search-results []
   :sample {:selected {:id ""
                       :name ""}
            :stages {:list []
                     :token nil}
            :active-stage {:id nil
                           :file-spec []
                           :upload {:file nil
                                    :progress 0.0
                                    :state :default}}
            :new-stage {:method nil
                        :annotation ""}}
   :mutable {:resumable nil}})

(def state
  (reagent/atom null-state))

(defn clear
  "Reset the part of the state tree denoted by `path` to its default state."
  [state path]
  (assoc-in state path (get-in null-state path)))

(defn copy-state
  "Copy elements of the application state from `src` to `dst`; `paths` is a
  collection of sequences, where each is a path that might be used with
  `assoc-in` or `get-in`.  This function makes no attempt to optimise the
  process by looking for longer paths that may be contained within shorter
  ones."
  [dst src paths]
  (loop [paths paths
         dst   dst]
    (if (seq paths)
      (let [path (first paths)]
        (recur (rest paths)
               (assoc-in dst path (get-in src path))))
      dst)))

;; ------------------------------------------------------- subscriptions --- ;;

(register-sub
 :query/ui-enabled?
 (fn [state [query-id]]
   (assert (= query-id :query/ui-enabled?))
   (letfn [(neither-nil-nor-empty? [x]
             (and (not (nil? x)) (not (empty? x))))]
     (reaction (neither-nil-nor-empty? (:name (:project @state)))))))

(register-sub
 :query/projects
 (fn [state [query-id]]
   (assert (= query-id :query/projects))
   (reaction (get-in @state [:cached :projects]))))

(register-sub
 :query/methods
 (fn [state [query-id]]
   (assert (= query-id :query/methods))
   (reaction (get-in @state [:cached :methods]))))

(register-sub
 :query/active-project
 (fn [state [query-id]]
   (assert (= query-id :query/active-project))
   (reaction (:project @state))))

(register-sub
 :query/sample-search-terms
 (fn [state [query-id]]
   (assert (= query-id :query/sample-search-terms))
   (reaction (get-in @state [:search-terms]))))

(register-sub
 :query/sample-search-results
 (fn [state [query-id]]
   (assert (= query-id :query/sample-search-results))
   (reaction (get @state :search-results))))

(register-sub
 :query/selected-sample
 (fn [state [query-id]]
   (assert (= query-id :query/selected-sample))
   (reaction (get-in @state [:sample :selected]))))

(register-sub
 :query/sample-stages
 (fn [state [query-id]]
   (assert (= query-id :query/sample-stages))
   (reaction {:stages     (get-in @state [:sample :stages :list])
              :next-stage (get-in @state [:sample :stages :token])
              :active     (get-in @state [:sample :active-stage :id])})))

(register-sub
 :query/sample-stage-detail
 (fn [state [query-id]]
  (assert (= query-id :query/sample-stage-detail))
  (reaction (get-in @state [:sample :active-stage]))))

(register-sub
 :query/sample-stage-input
 (fn [state [query-id]]
   (assert (= query-id :query/sample-stage-input))
   (reaction (-> @state
                 (get-in [:sample :new-stage])
                 (assoc :id (get-in @state [:sample :stages :token]))))))
