# Day 46 — Sipho responds to second info request + trust re-check + isolation spot-check `[PORTAL]`

**Date**: 2026-06-13
**Cycle**: 27
**Stack**: Keycloak dev stack (portal :3002, backend :8080 PID 16741, Mailpit :8025)
**Actor**: Sipho Dlamini — portal magic-link session on :3002.
**Tooling**: **Playwright MCP exclusively** (clean Chromium). DB reads via `docker exec b2b-postgres psql -U postgres -d docteams`; Mailpit API for the magic-link.
**Context swap**: firm (Bob) → portal (Sipho).
**Harness friction**: Playwright SingletonLock recurred on first navigate; cleared as before (`rm SingletonLock/SingletonCookie/SingletonSocket` + pkill) — not a defect.

---

## Balance note (scenario vs this cycle — NOT a defect, carries over from Day 45)

Scenario 46.4/46.5 expect **R 71,000** and **three** deposits (R50k Day 10 + R1,000 Day 14 carry-over + R20k Day 45). The scenario's own amendment note (cycle 18) explains the R1,000 = a one-off Day-14 cycle-15 OBS-1101 Mailpit-formatting verify deposit. **This cycle has NO R1,000 Day-14 deposit** (Day 14 recorded only Moroka's R25,000). DB confirms Sipho's only deposits are DEP/2026/001 R50,000 + DEP/2026/003 R20,000. **Therefore this cycle's correct post-Day-45 Sipho balance is R 70,000 across exactly two deposits.** The R71,000 / three-deposit figure is a prior-cycle artifact, not a regression. (Consistent with the accepted Day-45 note.)

---

## Day 46 checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.1 | Login via magic-link for second info request (REQ-0004) | **PASS** | Mailpit `NfdFk5sgvpGJ2XCVYQn8en` link → `:3002/auth/exchange?token=[REDACTED-MAGIC-LINK-TOKEN]&orgId=mathebula-partners`. Exchanged → landed on `/projects` authenticated as **Sipho Dlamini**. Matters list shows only Sipho's 2 matters (RAF + engagement-letter project) — no Moroka. 0 JS errors (favicon 404 only). |
| 46.2 | `/home` → pending info request shows → click into it | **PASS** | `/home` card **"Pending info requests: 1"**. Click → `/requests` list shows **REQ-0004 / Dlamini v Road Accident Fund / SENT / 0/2 submitted**. REQ-0003 (Moroka's) correctly **absent** from Sipho's list. Click REQ-0004 → detail page: 2 required items (Hospital discharge summary, Orthopaedic report), 0/2 submitted, status SENT. (Portal lists ad-hoc requests by REQ number + matter, not the free-text firm-side title "Supporting medical evidence" — this build's ad-hoc requests carry content via items; consistent with Day 45 note.) |
| 46.3 | Upload 2 test PDFs (discharge summary, orthopaedic report) → submit → Submitted | **PASS** | Item 1: upload `hospital-discharge-summary.pdf` → **Upload and submit** → "Submitted — status: SUBMITTED", header advanced to **1/2 submitted • IN_PROGRESS**. Item 2: upload `orthopaedic-report.pdf` → **Upload and submit** → "Submitted — status: SUBMITTED", header **2/2 submitted • IN_PROGRESS**. DB: both `request_items` = SUBMITTED with `document_id` set + `submitted_at` set; `information_requests.status` = **IN_PROGRESS**. Envelope advanced SENT → IN_PROGRESS (all client items submitted, awaiting firm acceptance). Backend log: 0 ERROR / 0 rollback in the submission window. |
| 46.4 | `/trust` → balance shows **R 70,000** (cycle-adjusted from scenario R71,000 — see note) | **PASS** | `/trust` auto-resolves to Sipho's single matter (`08ad56c4`). **Trust balance R 70 000,00 / As of 13 Jun 2026**. Matches the two-deposit cycle reality. |
| 46.5 | Transaction list shows deposits ordered descending by running balance, dates + amounts correct | **PASS** (cycle-adjusted: **two** deposits, not three) | Table rows: (1) **13 Jun 2026 / DEPOSIT / Top-up per engagement letter / R 20 000,00 / running R 70 000,00** (top); (2) **13 Jun 2026 / DEPOSIT / Initial trust deposit — RAF-2026-001 / R 50 000,00 / running R 50 000,00**. Newest-first by running balance; both dates 13 Jun 2026 (single-day E2E run); amounts correct. Three-deposit expectation reduces to two for the same carry-over reason (no R1,000 deposit this cycle). 📸 `day-46-portal-trust-two-deposits.png`. |
| 46.6 | Passive isolation spot-check — only Sipho's matter trust; no Moroka R25,000 merged in | **PASS** | Trust page shows only Sipho's matter, balance R 70,000, two deposits — **no R 25,000 Moroka deposit anywhere**. DB: Moroka's DEP/2026/002 R25,000 lives on a separate trust account, never aggregated. Matters list, requests list, and trust all scoped to Sipho. Isolation holds 31 scenario-days after the explicit Day-15 check. |
| 46.7 | `/home` → pending info requests no longer shows the medical evidence request | **PASS** | After submission, `/home` card **"Pending info requests: 0"** (was 1). "Last trust movement R 20 000,00 / 13 Jun 2026" still correct. |
| 46.8 | 📸 Optional screenshot | **PASS** | `qa_cycle/checkpoint-results/day-46-portal-trust-two-deposits.png` captured. |

---

## Day 46 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Second info request lifecycle complete | **PASS** | REQ-0004 both items uploaded + submitted via portal UI; envelope SENT → IN_PROGRESS; DB confirms 2× SUBMITTED with documents (46.2, 46.3, 46.7). |
| Trust balance update visible on portal (both deposits) | **PASS** | `/trust` = R 70,000 with both deposits (R50k + R20k) listed newest-first; cycle-correct (R71,000 / third-deposit figure is a prior-cycle artifact) (46.4, 46.5). |
| Isolation holds — no Moroka data leak 31 days after the explicit check | **PASS** | Zero Moroka data on any portal surface (matters, requests, trust); Moroka R25,000 never aggregated (46.1, 46.2, 46.6). |

---

## Console / backend health
- **Portal (all routes this session)**: only `:3002/favicon.ico` 404 (benign) — **0 genuine JS errors**.
- **Backend log**: 0 ERROR / 0 rollback across both item submissions. Only a startup CglibAopProxy WARN (benign, unrelated).

## Carry-over exemptions observed (noted, not re-filed)
- **R1,000 Day-14 carry-over deposit** — not present this cycle; scenario's R71,000 / three-deposit vs observed R70,000 / two-deposit is the prior-cycle artifact described above, not a defect.
- **OBS-201** (firm-side assistant 404) — N/A on portal this day.

## New gaps
- **None.**

## Result
**Day 46: 8/8 step checkpoints PASS + 3/3 summary checkpoints PASS; 0 new gaps; NOT blocked.** Sipho responded to the second info request (REQ-0004) via the portal — both items (Hospital discharge summary + Orthopaedic report) uploaded and submitted, envelope advanced SENT → IN_PROGRESS. Trust balance reconciles to **R 70,000** across two deposits (cycle-correct). Tenant isolation holds 31 scenario-days after the Day-15 explicit check (zero Moroka data). `/home` pending count updated 1 → 0. Zero genuine JS errors. **Day 60 next** (Firm matter closure + Statement of Account — note 60.2 expects REQ-0003's 2 items submitted by Sipho on Day 46; that maps to **REQ-0004** this cycle).

Screenshots: `day-46-portal-trust-two-deposits.png`.
