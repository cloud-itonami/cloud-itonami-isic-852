# cloud-itonami-isic-852

**Secondary Education** — ISIC Rev.4 class 852.

A coordination-only actor for secondary education institutions (middle and high school), behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Features

- **Closed proposal-op allowlist**: log-attendance-note, schedule-parent-guardian-meeting, coordinate-supply-request, schedule-staff-shift-proposal, flag-safety-concern (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Student verified** — target must exist AND be registered/verified in the store.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — academic grading, curriculum decisions, disciplinary action, special-education/IEP, custody determinations, and safety-authority overrides are permanently blocked.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: attendance logging only (approval-gated)
  - Phase 2: + family meeting, supply, shift proposals (approval-gated)
  - Phase 3: auto-commits clean proposals (safety concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## Development

```bash
# Install dependencies
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/secondaryops/governor_test.clj` — unit tests of governor hard checks and scope exclusion
- `test/secondaryops/advisor_test.clj` — advisor proposal shape and consistency
- `test/secondaryops/phase_test.clj` — rollout phase logic
- `test/secondaryops/governor_contract_test.clj` — full graph integration, audit trail
- `test/secondaryops/store_contract_test.clj` — Store protocol and MemStore implementation

## Modules

- `secondaryops.store` — SSoT (MemStore, String-keyed student directory, append-only ledger)
- `secondaryops.advisor` — contained intelligence node (mock + real-LLM seam)
- `secondaryops.governor` — independent compliance layer
- `secondaryops.phase` — staged rollout (0→3)
- `secondaryops.operation` — langgraph-clj StateGraph
- `secondaryops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-services) fleet. See ADR-2607121000 and ADR-2607152800+ for design decisions.
