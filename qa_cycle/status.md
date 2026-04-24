# QA Cycle Status — Legal ZA Full Lifecycle (VERIFY CYCLE, Keycloak) — 2026-04-24

## Current State

**Purpose**: Re-validate that the 40+ fixes merged to `main` between 2026-04-21 and 2026-04-24 actually work end-to-end on the Keycloak dev stack. Prior cycle state archived to `qa_cycle/_archive_2026-04-24_pre-verify/status.md`.

- **ALL_DAYS_COMPLETE**: false
- **QA Position**: Day 0 — 0.1 (fresh run; no shortcuts)
- **Cycle**: 1 (verify)
- **Dev Stack**: PARTIAL — backend/gateway/frontend UP from prior session but running stale code (backend PID 42352 from L-60 restart, has NOT picked up ~15 subsequent merges); portal STALE (down). Must restart backend + start portal before QA starts.
- **NEEDS_REBUILD**: true (backend — all code post-L-60 merge)
- **Branch**: `bugfix_cycle_2026-04-24`
- **Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`
- **Auth Mode**: Keycloak (real OIDC) for firm; magic-link + portal JWT for portal (`:3002`)

## Verification Focus — fixes to re-check as QA proceeds

These gaps were **FIXED and merged to main** since 2026-04-21. QA should re-verify them during the lifecycle run (not as separate probes — just notice whether the underlying functionality works during the normal flow). If any of these REOPEN, log as regression.

### Shipped in the 2026-04-21 cycle PR #1114 (bundled; verified end-to-end up to Day 28.3 before merge to main)
- L-23 (settings general 500 error), L-33 (FICA onboarding pack), L-34 (portal-contact auto-provision), L-42 (info-request magic-link), L-43 (portal request-item submitted listener), L-50 (acceptance email portal base URL), L-56 (time-entry PROSPECT gate), L-57 (disbursement Matter combobox), L-60 (invoice PROSPECT gate), P-01 (portal InfoRequestsCard path), P-02 (portal requests UI route), P-06 (portal acceptance status handling)

### Shipped post-cycle-merge (2026-04-23 → 2026-04-24) — NEW functionality / reopened code paths
- **L-21** — Access-request detail: WONT_FIX (PR #1128, scenario rescope, no code).
- **L-22** — BFF session handoff cleaner middleware (PR #1125). Verify: after Thandi registers while padmin session is live, Thandi lands clean without padmin sidebar leak.
- **L-25** — SECTION_86 trust account type (PR #1123). Verify: Day 1 Trust Account dialog offers SECTION_86 option.
- **L-27** — Tax rate label "VAT — Standard" + `tax_label='VAT'` on legal-za (PR #1124).
- **L-28** — Conflict-check self-exclusion (PR #1118). Verify: Day 2 conflict check on newly-created client returns NO_CONFLICT (not CONFLICT_FOUND self-match).
- **L-29** — Conflict-check form dropdowns hydrated (PR #1122).
- **L-31** — Customers empty-state vertical copy (PR #1122).
- **L-32** — Create Client redirects to new detail (PR #1122).
- **L-35** — Custom-field update on PROSPECT customers allowed (PR #1119). Verify: Day 3 matter Save Custom Fields on PROSPECT customer returns 200.
- **L-37** — Field-group over-attach narrowed (PR #1122).
- **L-38** — Matter detail tab cleanup (PR #1122).
- **L-39** — customerId URL-param propagation to Configure step (PR #1122).
- **L-41** — Information request due_date column + picker (PR #1123).
- **L-44** — PackReconciliationRunner syncs enabled_modules + enabled_features (PR #1124).
- **L-45** — Per-item Download on firm info-request detail (PR #1122).
- **L-46** — FICA status tile on matter Overview (PR #1127).
- **L-47** — Portal parent-request status sync (PR #1124).
- **L-48** — Matter-level "+ New Engagement Letter" CTA + `/api/customers` 404 fix (PR #1127).
- **L-51** — "Send for Acceptance" email subject keywords (PR #1124).
- **L-52** — Portal trust-ledger sync for RECORDED deposits (PR #1117). Verify: Day 11 Sipho portal `/trust` shows the Day-10 deposit (R 50 000).
- **L-53** — Liquidation & Distribution request pack (PR #1123).
- **L-55** — Portal trust error-message alignment (PR #1124).
- **L-58** — Court dates union into Overview deadlines (part of PR #1124 — listed as SKIPPED there; re-check).
- **L-62** — Tax number hybrid: INDIVIDUAL auto-populate, soft-warn at draft, hard-enforce at send (PR #1126). Verify: Day 28 fee-note draft succeeds with tax_number warning, send fails without.
- **L-63** — Fee-note dialog surfaces unbilled disbursements (PR #1116). Verify: Day 28 fee-note dialog shows the Day-21 Sheriff Fees R 1 250 (after it's been Approved).
- **P-03** — Portal projects visible to portal contacts linked via customer (PR #1120). Verify: Day 4 Sipho portal `/projects` shows his RAF matter (not empty).
- **P-07** — Portal `/documents` nav hidden or stubbed (PR #1123).

### CodeRabbit follow-ups (all merged)
- PR #1115, #1121 — tenantId guard + PII log redaction + a11y label + scheduler-path metadata + test hardening.

## Environment

| Service | URL | Status (pre-verify) |
|---------|-----|---------------------|
| Frontend (kc mode) | http://localhost:3000 | UP PID 33007 (HMR should pick up current main; may need restart to be safe) |
| Backend (local+keycloak profile) | http://localhost:8080 | UP PID 42352 but STALE — needs restart for all post-L-60 merges |
| Gateway (BFF) | http://localhost:8443 | UP external PID 71426 |
| Portal | http://localhost:3002 | DOWN — stale PID, needs restart |
| Keycloak | http://localhost:8180 | expected UP |
| Mailpit UI | http://localhost:8025 | expected UP |
| Postgres (docteams) | localhost:5432 | expected UP |
| LocalStack (S3) | http://localhost:4566 | expected UP |

Infra agent: restart backend, start portal, verify all 4 services green, confirm Docker infra health. Clear NEEDS_REBUILD.

## Gap Tracker

| GAP_ID | Day / Checkpoint | Severity | Status | Summary | Owner | Retries |
|--------|------------------|----------|--------|---------|-------|---------|

(No OPEN gaps carried in — they were all closed via merge or WONT_FIX. New gaps discovered during verify will be added here as OPEN.)

## Deferred to future phases (do NOT re-log as new gaps if observed)

- **L-30** — KYC adapter (needs provider decision; mock-adapter wiring planned for Sprint 2).
- **L-36** — RAF-specific matter template (Sprint 2).
- **L-40** — Multi-role portal-contact CRUD (Sprint 2).
- **L-49** — Proposal fee-estimate side-table + line-item rendering (Sprint 3 — scenario 7.3/7.4/8.2/8.3 carry SKIPPED-BY-DESIGN).
- **L-54** — Beneficial-owners structured field group for TRUST/COMPANY (Sprint 3).
- **L-61** — Bulk Billing Runs default ON for legal-za (Sprint 2).
- **OBS-L-27** — Portal PDF iframe cross-origin (infra decision).

## Legend

- **Status**: OPEN → SPEC_READY → FIXED → VERIFIED | REOPENED | STUCK | WONT_FIX | RESOLVED
- **Severity**: HIGH (blocker) / MED (cascading bug) / LOW (cosmetic)
- **Owner**: QA / Product / Dev / Infra

## Log

- 2026-04-24 SAST — VERIFY CYCLE started. Prior cycle archived to `qa_cycle/_archive_2026-04-24_pre-verify/status.md`. Branch: `bugfix_cycle_2026-04-24` off main. Purpose: re-validate 40+ shipped fixes end-to-end before declaring Day 0–90 scenario green. Next action: Infra agent — restart backend for post-L-60 merges + start portal.
