(ns secondaryops.advisor-test
  "Unit tests of `secondaryops.advisor` proposal generation."
  (:require [clojure.test :refer [deftest is testing]]
            [secondaryops.advisor :as adv]
            [secondaryops.store :as store]))

(def db (store/seed-db))

(deftest propose-attendance-note-shape
  (testing "attendance-note proposal has correct shape and fields"
    (let [p (adv/infer db {:op :log-attendance-note
                           :student-id "resident-1"
                           :patch {:meal "lunch" :mood "cheerful"}})]
      (is (= :log-attendance-note (:op p)))
      (is (= "resident-1" (:student-id p)))
      (is (= :propose (:effect p)))
      (is (<= 0 (:confidence p) 1))
      (is (map? (:value p)))
      (is (contains? (:value p) :student-id)))))

(deftest propose-parent-guardian-meeting-shape
  (testing "parent-guardian-meeting proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-parent-guardian-meeting
                           :student-id "resident-2"
                           :patch {:visitor "son" :date "2026-07-20"}})]
      (is (= :schedule-parent-guardian-meeting (:op p)))
      (is (= "resident-2" (:student-id p)))
      (is (= :propose (:effect p))))))

(deftest propose-supply-request-shape
  (testing "supply-request proposal has correct shape"
    (let [p (adv/infer db {:op :coordinate-supply-request
                           :student-id "resident-1"
                           :patch {:item "linens" :quantity 2}})]
      (is (= :coordinate-supply-request (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest propose-staff-shift-shape
  (testing "staff-shift proposal has correct shape"
    (let [p (adv/infer db {:op :schedule-staff-shift-proposal
                           :student-id "resident-1"
                           :patch {:caregiver "Chen" :shift "morning"}})]
      (is (= :schedule-staff-shift-proposal (:op p)))
      (is (= :propose (:effect p)))
      (is (>= (:confidence p) 0.85)))))

(deftest propose-safety-concern-shape
  (testing "safety-concern proposal always escalates"
    (let [p (adv/infer db {:op :flag-safety-concern
                           :student-id "resident-1"
                           :patch {:concern "fall risk observed"}})]
      (is (= :flag-safety-concern (:op p)))
      (is (= :propose (:effect p)))
      (is (string? (:summary p))))))

(deftest all-proposals-effect-is-always-propose
  (testing "every proposal type has :effect :propose, never direct actuation"
    (doseq [op [:log-attendance-note :schedule-parent-guardian-meeting :coordinate-supply-request
                :schedule-staff-shift-proposal :flag-safety-concern]]
      (let [p (adv/infer db {:op op :student-id "resident-1" :patch {}})]
        (is (= :propose (:effect p))
            (str "op " op " must have :effect :propose"))))))

(deftest rationale-string-is-present
  (testing "every proposal has a rationale explaining the advisor's thinking"
    (doseq [op [:log-attendance-note :schedule-parent-guardian-meeting :coordinate-supply-request
                :schedule-staff-shift-proposal :flag-safety-concern]]
      (let [p (adv/infer db {:op op :student-id "resident-1" :patch {}})]
        (is (string? (:rationale p))
            (str "op " op " must have a :rationale string"))))))
