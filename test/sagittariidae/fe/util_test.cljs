
(ns sagittariidae.fe.util-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [sagittariidae.fe.util :as u]))

(deftest test:values-by-sorted-key
  (testing "is lazy"
    (is (not (realized? (u/values-by-sorted-key {:c 0 :b 1 :a 2})))))
  (testing "values are ordered by key: keyword"
    (is (= '(2 1 0) (u/values-by-sorted-key {:c 0 :b 1 :a 2})))
    (is (= '(0 1 2) (u/values-by-sorted-key {:b 1 :a 0 :c 2}))))
  (testing "values are ordered by key: int"
    (is (= '(:a :b :c) (u/values-by-sorted-key {2 :c 1 :b 0 :a})))
    (is (= '(:a :b :c) (u/values-by-sorted-key {1 :b 0 :a 2 :c})))))
