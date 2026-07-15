(ns secondaryops.governor
  "SecondaryOpsGovernor -- the independent compliance layer for secondary
  administrative coordination. The advisor has no notion of whether a
  student is actually registered and verified in the secondary roster,
  whether its own proposed `:effect` secretly claims a direct actuation
  instead of a mere proposal, or whether it has silently drifted into
  a permanently out-of-scope decision area, so this MUST be a separate
  system able to *reject* a proposal and fall back to HOLD.

  This actor's scope is ADMINISTRATIVE COORDINATION ONLY (attendance
  logging, parent/guardian meeting scheduling, supply coordination,
  staff shift proposals, safety-concern flagging). It NEVER performs
  or authorizes:
    - grading or academic-performance assessment decisions
    - curriculum selection or pedagogical decisions
    - disciplinary action, suspension, or expulsion decisions
    - special-education (IEP) or academic support determinations
    - custody, guardianship, or family-court involvement decisions
    - safety-authority overrides (child-protective-services coordination,
      law-enforcement liaison, or compliance investigations beyond
      escalation/flagging)

  Three HARD checks, ALL permanent, un-overridable by any human
  approval:

    1. Student unverified       -- the target student record must exist
                                   AND be independently confirmed
                                   `:registered?`/`:verified?` in the
                                   store before ANY proposal for it may
                                   commit or even escalate. Never trusts
                                   a proposal's own claim about the
                                   student -- re-derived from the
                                   student's own store record, the same
                                   'ground truth, not self-report'
                                   discipline every sibling actor's
                                   governor uses.
    2. Effect not :propose      -- every proposal's `:effect` MUST be
                                   `:propose`. Any other effect value
                                   is, by construction, a claim to
                                   directly actuate/commit outside
                                   governance -- HARD block, not
                                   merely low-confidence.
    3. Scope exclusion          -- ANY proposal (regardless of op)
                                   whose op, rationale, summary,
                                   citations or draft value touches
                                   grading/academic-assessment/
                                   curriculum/pedagogical/disciplinary/
                                   special-education/custody/
                                   safety-authority territory is a
                                   HARD, PERMANENT block -- this
                                   actor's charter excludes that
                                   territory structurally, not as a
                                   rollout milestone. Evaluated
                                   UNCONDITIONALLY on every proposal.
                                   An op outside the closed five-op
                                   allowlist is the SAME failure mode
                                   (an advisor proposing something it
                                   was never authorized to propose) and
                                   is folded into this same check.
                                   Legitimate safety-concern flagging
                                   is never itself blocked.

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-safety-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `secondaryops.phase` independently agrees: `:flag-safety-concern` is
  never a member of any phase's `:auto` set either -- two layers, not
  one."
  (:require [clojure.string :as str]
            [secondaryops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`)."
  #{:log-attendance-note :schedule-parent-guardian-meeting
    :coordinate-supply-request :schedule-staff-shift-proposal
    :flag-safety-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- grading, pedagogical
  decisions, disciplinary action, special-education determinations,
  custody/family-court involvement, or safety-authority enforcement
  beyond escalation. Scanned across the proposal's op/summary/rationale/
  cites/value, never trusting the advisor's own framing of its intent.
  Qualified terms (e.g. 'grade' within 'grade scale', 'behavior'
  within 'behavior incident', not bare keywords) to avoid false blocks
  of legitimate safety-concern flagging (e.g. 'student disclosed a
  behavior concern at home')."
  ["grade" "成績" "grading" "採点" "academic performance" "academic-performance"
   "学習成績" "assess" "測定" "assessment" "評価" "curriculum" "カリキュラム"
   "pedagogical" "教育学的" "lesson plan" "lesson-plan" "授業計画"
   "discipline" "規律" "disciplinary action" "disciplinary-action" "懲罰処分"
   "suspension" "停学" "expulsion" "退学" "conduct" "品行" "behavio" "行動"
   "ien" "individualized-education-plan" "iep" "特別支援教育計画" "特支"
   "special education" "special-education" "特別支援" "iep meeting" "iep-meeting"
   "custody" "親権" "guardianship" "後見人" "family court" "family-court"
   "child protect" "児童保護" "cps" "cas" "law enforcement" "law-enforcement"
   "police" "警察" "complaint invest" "complaint-invest" "苦情調査"
   "compliance enforce" "compliance-enforce" "違反調査"])

;; ----------------------------- checks -----------------------------

(defn- student-unverified-violations
  "The target student must exist AND be independently `:registered?`/
  `:verified?` in the store -- never trust the proposal's own
  `:student-id` claim without a store lookup."
  [{:keys [student-id]} st]
  (let [s (store/student st student-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :student-unverified
        :detail (str student-id " は未登録または未検証の生徒 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion scan checks."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches grading/pedagogical/disciplinary/
  special-education/custody/safety-authority territory, regardless of
  confidence or how clean every other check is. Evaluated UNCONDITIONALLY
  on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "採点/教育学的判断/懲罰処分/特別支援判定/親権判断/安全当局の領域に触れる提案は永久に禁止"}])))

(defn check
  "Censors a SecondaryOpsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal store]
  (let [student-id (or (:student-id proposal) (:student-id request))
        hard (into []
                   (concat (student-unverified-violations {:student-id student-id} store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :student-id (:student-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
