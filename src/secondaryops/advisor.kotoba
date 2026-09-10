(ns secondaryops.advisor
  "SecondaryOpsAdvisor -- the *contained intelligence node* for the
  ISIC-851 primary-education operations-coordination actor.

  It drafts exactly five kinds of back-office proposal from a closed
  allowlist: attendance logging, parent/guardian meeting scheduling,
  supply coordination, staff shift proposals, and safety-concern flagging.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record
  and NEVER a direct actuation -- every proposal's `:effect` is always
  `:propose`. Every output is censored downstream by `secondaryops.governor`
  before anything touches the SSoT.

  This advisor NEVER drafts grading decisions, academic assessment,
  curriculum selection, pedagogical decisions, disciplinary action/
  suspension/expulsion, special-education/IEP determinations, custody/
  guardianship decisions, or safety-authority actions -- those are
  permanently out of scope for this actor, not merely un-implemented.
  `secondaryops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised
  or confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :student-id str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-attendance-note
  "Draft an attendance log entry. Pure logging of present/absent/tardy status,
  pickup/dropoff logistics -- never an academic or behavioral assessment."
  [_db {:keys [student-id patch]}]
  {:op         :log-attendance-note
   :student-id student-id
   :summary    (str student-id " の出欠記録: " (pr-str (keys patch)))
   :rationale  "生徒の出席状況と登下校の物流を記録します。"
   :cites      [student-id]
   :effect     :propose
   :value      (merge {:student-id student-id} patch)
   :confidence 0.94})

(defn- propose-parent-guardian-meeting
  "Draft a parent/guardian meeting scheduling proposal (a calendar
  entry, never a direct dispatch)."
  [_db {:keys [student-id patch]}]
  {:op         :schedule-parent-guardian-meeting
   :student-id student-id
   :summary    (str student-id " の保護者面談予定を提案: " (pr-str (keys patch)))
   :rationale  "生徒と保護者の面談時間を調整します。"
   :cites      [student-id]
   :effect     :propose
   :value      (merge {:student-id student-id} patch)
   :confidence 0.89})

(defn- propose-supply-request
  "Draft a consumable supply request coordination (classroom/office/cafeteria
  supplies -- never curriculum materials selection or instructional content)."
  [_db {:keys [student-id patch]}]
  {:op         :coordinate-supply-request
   :student-id student-id
   :summary    (str "クラス/生徒関連の消耗品リクエスト: " (pr-str (keys patch)))
   :rationale  "教室・事務・給食の消耗品を調達します。"
   :cites      [student-id]
   :effect     :propose
   :value      (merge {:student-id student-id} patch)
   :confidence 0.91})

(defn- propose-staff-shift
  "Draft a staff-shift roster PROPOSAL only (never a binding assignment,
  never a teaching-qualification or assignment decision)."
  [_db {:keys [student-id patch]}]
  {:op         :schedule-staff-shift-proposal
   :student-id student-id
   :summary    (str "職員シフト提案: " (pr-str (keys patch)))
   :rationale  "職員のシフト時間を提案します。"
   :cites      [student-id]
   :effect     :propose
   :value      (merge {:student-id student-id} patch)
   :confidence 0.87})

(defn- propose-safety-concern
  "Surface a student/secondary safety concern (suspected abuse/neglect,
  bullying reports, injury, wellbeing incidents) for HUMAN triage.
  This op ALWAYS escalates in `secondaryops.governor` -- never auto-committed
  at any phase -- regardless of how confident the advisor is that the
  concern is real."
  [_db {:keys [student-id patch]}]
  {:op         :flag-safety-concern
   :student-id student-id
   :summary    (str student-id " の安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "生徒の身体的・心理的安全に関する観察事実を報告します。"
   :cites      [student-id]
   :effect     :propose
   :value      (merge {:student-id student-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-attendance-note (propose-attendance-note _db request)
                   :schedule-parent-guardian-meeting (propose-parent-guardian-meeting _db request)
                   :coordinate-supply-request (propose-supply-request _db request)
                   :schedule-staff-shift-proposal (propose-staff-shift _db request)
                   :flag-safety-concern (propose-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually modified grades and curriculum")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :student-id (:student-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
