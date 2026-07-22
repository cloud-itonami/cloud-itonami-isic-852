(ns secondaryops.store
  "SSoT for the ISIC-851 primary-education COORDINATION actor,
  behind a `Store` protocol so the backend is a swap, not a rewrite -- the
  same seam every `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the back-office operations of a primary secondary:
  attendance logging, parent/guardian meeting scheduling, consumable supply
  coordination (classroom/office supplies), staff shift proposals, and
  safety-concern flagging (wellbeing incidents, suspected abuse/neglect).
  It never touches grading, academic assessment, curriculum selection,
  pedagogical decisions, disciplinary action/suspension/expulsion,
  special-education/IEP determinations, custody/guardianship decisions,
  or any safety-authority override -- see `secondaryops.governor`'s
  `scope-exclusion-violations`, a HARD, permanent, un-overridable block.

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/demo
  (no deps). A `students` directory keyed by `:student-id` STRING (never a
  keyword -- consistent keying from the start, avoiding the silent-miss
  bug that plagued an earlier shepherd attempt).

  A registered/verified student record must exist before ANY proposal
  for that student may ever commit or escalate -- `secondaryops.governor`'s
  `student-unverified-violations` re-derives this from the student's own
  `:registered?`/`:verified?` fields, never from proposal self-report,
  the SAME 'ground truth, not self-report' discipline every sibling
  actor's own governor uses.

  The ledger stays append-only: which student a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log.

  `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible EAV
  store (datalog q / pull / upsert). Pure `.cljc`, so it runs offline AND
  can be pointed at a real Datomic Local or a kotoba-server pod by
  swapping `langchain.db`'s `:db-api` (see `langchain.kotoba-db`) -- the
  same seam `cloud-itonami-isic-7810`'s `employmentops.store` and every
  other flagship-tier sibling actor's store uses. Both backends satisfy
  the SAME `Store` protocol and pass the same contract
  (`test/secondaryops/store_contract_test.clj`), which is the whole
  point: the actor, `secondaryops.governor` and the audit ledger never
  know which SSoT they run on."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [langchain.db :as d]))

(defprotocol Store
  (student [s student-id] "Registered student record, or nil.
    Student map: {:student-id .. :name .. :grade .. :registered? bool :verified? bool}.")
  (all-students [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed coordination-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-students [s students] "replace/seed the student directory (map student-id->student)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained student directory covering both the happy path
  and the governor's own hard checks, so the actor + tests run offline."
  []
  {:students
   {"student-1" {:student-id "student-1" :name "Aiko Yamada" :grade "3A"
                  :registered? true :verified? true}
    "student-2" {:student-id "student-2" :name "Kenji Sato" :grade "4B"
                  :registered? true :verified? true}
    "student-3" {:student-id "student-3" :name "Yuki Nakamura" :grade "3C"
                  :registered? true :verified? false}}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (student [_ student-id] (get-in @a [:students student-id]))
  (all-students [_] (sort-by :student-id (vals (:students @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (commit-record! [_ record]
    (swap! a update :coordination-log conj record)
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-students [s students] (when (seq students) (swap! a assoc :students students)) s))

(defn seed-db
  "A MemStore seeded with the demo student directory. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :coordination-log []))))

(defn mem-store
  "A MemStore seeded with an explicit `students` map (student-id string ->
  student map) -- the primary test/dev entry point. `students` may be empty
  (an unregistered-everywhere store)."
  [students]
  (->MemStore (atom {:students (or students {}) :ledger [] :coordination-log []})))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (coordination-proposal records, ledger facts) are
  stored as EDN strings so `langchain.db` doesn't expand them into
  sub-entities -- the same convention every sibling actor's store uses."
  {:student/id               {:db/unique :db.unique/identity}
   :ledger/seq               {:db/unique :db.unique/identity}
   :coordination-record/seq  {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- student->tx [{:keys [student-id name grade registered? verified?]}]
  (cond-> {:student/id student-id}
    name                   (assoc :student/name name)
    grade                  (assoc :student/grade grade)
    (some? registered?)    (assoc :student/registered? registered?)
    (some? verified?)      (assoc :student/verified? verified?)))

(def ^:private student-pull
  [:student/id :student/name :student/grade :student/registered? :student/verified?])

(defn- pull->student [m]
  (when (:student/id m)
    {:student-id (:student/id m) :name (:student/name m) :grade (:student/grade m)
     :registered? (boolean (:student/registered? m)) :verified? (boolean (:student/verified? m))}))

(defrecord DatomicStore [conn]
  Store
  (student [_ student-id]
    (pull->student (d/pull (d/db conn) student-pull [:student/id student-id])))
  (all-students [_]
    (->> (d/q '[:find [?id ...] :where [?e :student/id ?id]] (d/db conn))
         (map #(pull->student (d/pull (d/db conn) student-pull [:student/id %])))
         (sort-by :student-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (coordination-log [_]
    (->> (d/q '[:find ?s ?r :where [?e :coordination-record/seq ?s] [?e :coordination-record/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (commit-record! [s record]
    (d/transact! conn [{:coordination-record/seq (count (coordination-log s))
                        :coordination-record/record (enc record)}])
    record)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-students [s students]
    (when (seq students) (d/transact! conn (mapv student->tx (vals students))))
    s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `students`
  (student-id string -> student map); empty when omitted."
  ([] (datomic-store {}))
  ([students]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-students s students))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo student directory -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (:students (demo-data))))
