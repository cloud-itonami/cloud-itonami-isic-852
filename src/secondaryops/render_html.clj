(ns secondaryops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300
  pattern, ported from cloud-itonami-isic-851's own
  `schoolops.render-html`): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`secondaryops.operation` -> `secondaryops.governor` ->
  `secondaryops.store`) through a scenario built from the actor's OWN
  seeded demo data (`secondaryops.store/seed-db`, students
  student-1/student-2/student-3) and renders the result
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [secondaryops.store :as store]
            [secondaryops.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :school-office-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: student-1 clears four ops that all auto-commit
  clean at phase 3 (log-attendance-note, schedule-parent-guardian-meeting,
  coordinate-supply-request, schedule-staff-shift-proposal); student-1's
  safety-concern flag ALWAYS escalates (per `always-escalate-ops`) even
  though clean, and is approved by a human; student-3 (registered but
  NOT `:verified?` in the seed data) HARD-holds on `:student-unverified`
  -- never reaches a human; a proposal whose advisor drifted into
  out-of-scope territory (`:out-of-scope? true`) HARD-holds on
  `:scope-excluded` -- also never reaches a human. Returns the resulting
  store -- every field read by `render` below is real governor/store
  output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "s1-attendance" {:op :log-attendance-note :student-id "student-1"
                                   :patch {:status "present" :pickup "parent"}})
    (exec! actor "s1-meeting" {:op :schedule-parent-guardian-meeting :student-id "student-1"
                                :patch {:date "2026-08-03" :time "15:30"}})
    (exec! actor "s1-supply" {:op :coordinate-supply-request :student-id "student-1"
                               :patch {:item "textbooks" :quantity 30}})
    (exec! actor "s1-shift" {:op :schedule-staff-shift-proposal :student-id "student-1"
                              :patch {:staff "T. Ito" :shift "AM"}})

    (exec! actor "s1-safety" {:op :flag-safety-concern :student-id "student-1"
                               :patch {:concern "unexplained bruising reported by classmate"
                                       :confidence 0.9}})
    (approve! actor "s1-safety")

    (exec! actor "s3-supply" {:op :coordinate-supply-request :student-id "student-3"
                               :patch {:item "art supplies" :quantity 12}})

    (exec! actor "s2-scope" {:op :log-attendance-note :student-id "student-2"
                              :out-of-scope? true :patch {:status "present"}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger student-id]
  (last (filter #(= (:student-id %) student-id) ledger)))

(defn- status-cell [ledger student-id]
  (let [f (last-fact-for ledger student-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (case rule
          :student-unverified "<span class=\"critical\">HARD hold &middot; unverified student</span>"
          :scope-excluded "<span class=\"critical\">HARD hold &middot; scope-excluded</span>"
          (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- student-row [ledger {:keys [student-id name grade registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc student-id) (esc name) (esc grade)
          (if (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
              "<span class=\"warn\">registered, unverified</span>")
          (status-cell ledger student-id)))

(defn- ledger-row [{:keys [t op student-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc student-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract
  ;; (README `Ops` table, `secondaryops.governor`/`secondaryops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-attendance-note</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-parent-guardian-meeting</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-supply-request</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-staff-shift-proposal</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        students (store/all-students db)
        student-rows (str/join "\n" (map (partial student-row ledger) students))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-852 &middot; secondary-education operations coordination</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Secondary education back-office coordination (ISIC 852) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never touches grading/discipline/custody</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Students</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>secondaryops.store</code> via <code>secondaryops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Student</th><th>Name</th><th>Grade</th><th>Roster status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     student-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (SecondaryOps Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Grading, curriculum, discipline, IEP, custody and safety-authority territory are permanently out of scope — see governor scope-exclusion.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Student</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
