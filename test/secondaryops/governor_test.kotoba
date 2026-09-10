(ns secondaryops.governor-test
  "Pure unit tests of `secondaryops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-test`'s
  full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [secondaryops.governor :as gov]
            [secondaryops.store :as store]))

(def student-1 {:student-id "student-1" :name "Aiko Yamada" :grade "3A" :registered? true :verified? true})
(def student-3 {:student-id "student-3" :name "Yuki Nakamura" :grade "3C" :registered? true :verified? false})

(defn- clean-proposal [op student-id]
  {:op op :student-id student-id :summary "s" :rationale "routine admin coordination"
   :cites [student-id] :effect :propose :value {} :confidence 0.85})

(deftest student-unregistered-is-hard
  (testing "no student record at all -> HARD hold"
    (let [s (store/mem-store {"student-1" student-1})
          verdict (gov/check {} nil (clean-proposal :log-attendance-note "unknown-student") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:student-unverified} (map :rule (:violations verdict)))))))

(deftest student-unverified-is-hard
  (testing "student registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"student-3" student-3})
          verdict (gov/check {} nil (clean-proposal :log-attendance-note "student-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:student-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"student-1" student-1})
          verdict (gov/check {} nil (assoc (clean-proposal :schedule-parent-guardian-meeting "student-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed five-op allowlist is a scope violation"
    (let [s (store/mem-store {"student-1" student-1})
          verdict (gov/check {} nil (clean-proposal :adjust-curriculum "student-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest grading-content-is-hard-and-permanent
  (testing "a proposal whose content touches grading/academic-assessment is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"student-1" student-1})
          poisoned (assoc (clean-proposal :log-attendance-note "student-1")
                          :rationale "assess student performance and assign grade scale"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest curriculum-content-is-hard
  (testing "a proposal touching curriculum/pedagogical decisions is HARD-blocked"
    (let [s (store/mem-store {"student-1" student-1})
          poisoned (assoc (clean-proposal :log-attendance-note "student-1")
                          :rationale "select curriculum and lesson plan materials"
                          :confidence 0.90)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest disciplinary-action-content-is-hard
  (testing "a proposal touching disciplinary action/suspension is HARD-blocked"
    (let [s (store/mem-store {"student-1" student-1})
          poisoned (assoc (clean-proposal :schedule-parent-guardian-meeting "student-1")
                          :summary "recommend suspension and disciplinary action")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest special-education-iep-content-is-hard
  (testing "a proposal touching special-education/IEP determinations is HARD-blocked"
    (let [s (store/mem-store {"student-1" student-1})
          poisoned (assoc (clean-proposal :coordinate-supply-request "student-1")
                          :value {:iep-decision "modify individualized education plan"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-safety-concern-is-not-scope-excluded
  (testing "flagging observed wellbeing concerns as a SAFETY CONCERN (not a disciplinary determination) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"student-1" student-1})
          concern (assoc (clean-proposal :flag-safety-concern "student-1")
                         :value {:concern "student disclosed a safety concern at home during class discussion"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (safety concerns/wellbeing) is exactly what this op exists to surface"))))
