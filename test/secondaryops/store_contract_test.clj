(ns secondaryops.store-contract-test
  "Contract tests for `secondaryops.store/Store` protocol."
  (:require [clojure.test :refer [deftest is testing]]
            [secondaryops.store :as store]))

(deftest mem-store-resident-lookup
  (testing "MemStore can store and retrieve students by ID (string keys)"
    (let [students {"r1" {:student-id "r1" :name "Alice" :registered? true :verified? true}}
          s (store/mem-store students)]
      (is (some? (store/student s "r1")))
      (is (nil? (store/student s "r99"))))))

(deftest mem-store-all-students
  (testing "MemStore returns all students in sorted order"
    (let [students {"r2" {:student-id "r2" :name "Bob"}
                     "r1" {:student-id "r1" :name "Alice"}
                     "r3" {:student-id "r3" :name "Carol"}}
          s (store/mem-store students)
          all-r (store/all-students s)]
      (is (= 3 (count all-r)))
      (is (= "r1" (:student-id (first all-r))))
      (is (= "r3" (:student-id (last all-r)))))))

(deftest mem-store-ledger-append
  (testing "MemStore append-ledger! adds facts to immutable log"
    (let [s (store/mem-store {})
          fact1 {:t :test :data "fact1"}
          fact2 {:t :test :data "fact2"}]
      (is (= 0 (count (store/ledger s))))
      (store/append-ledger! s fact1)
      (is (= 1 (count (store/ledger s))))
      (store/append-ledger! s fact2)
      (is (= 2 (count (store/ledger s)))))))

(deftest mem-store-coordination-log
  (testing "MemStore commit-record! appends to coordination-log"
    (let [s (store/mem-store {})
          record {:op :log-attendance-note :student-id "r1" :value {:meal "lunch"}}]
      (is (= 0 (count (store/coordination-log s))))
      (store/commit-record! s record)
      (is (= 1 (count (store/coordination-log s))))
      (is (= record (first (store/coordination-log s)))))))

(deftest mem-store-with-students
  (testing "MemStore with-students replaces the resident directory"
    (let [s (store/mem-store {})
          new-students {"r1" {:student-id "r1" :name "Alice"}}]
      (is (= 0 (count (store/all-students s))))
      (store/with-students s new-students)
      (is (= 1 (count (store/all-students s)))))))

(deftest seed-db-has-demo-data
  (testing "seed-db creates a populated MemStore with demo students"
    (let [s (store/seed-db)]
      (is (> (count (store/all-students s)) 0))
      (is (some? (store/student s "student-1")))
      (is (some? (store/student s "student-2")))
      (is (some? (store/student s "student-3"))))))

(deftest demo-data-string-key-consistency
  (testing "demo-data uses string keys, not keywords, for site-id"
    (let [demo (store/demo-data)
          students (:students demo)]
      (doseq [[k v] students]
        (is (string? k) "keys must be strings")
        (is (string? (:student-id v)) "student-id must be string")
        (is (= k (:student-id v)) "key must match student-id")))))

(deftest store-is-append-only
  (testing "appended facts are immutable and never removed"
    (let [s (store/seed-db)
          fact1 {:t :event1 :data "a"}
          fact2 {:t :event2 :data "b"}]
      (store/append-ledger! s fact1)
      (let [ledger-after-1 (store/ledger s)]
        (store/append-ledger! s fact2)
        (let [ledger-after-2 (store/ledger s)]
          (is (= (count ledger-after-1) (dec (count ledger-after-2))))
          (is (every? #(some (fn [x] (= x %)) ledger-after-2) ledger-after-1)
              "all prior facts must still be present"))))))
