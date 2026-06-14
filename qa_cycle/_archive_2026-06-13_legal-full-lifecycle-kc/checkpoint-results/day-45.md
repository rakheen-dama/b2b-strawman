# Day 45 — Firm: second info request + second trust deposit `[FIRM]`

**Date**: 2026-06-13
**Cycle**: 26
**Stack**: Keycloak dev stack (frontend :3000, backend :8080 PID 16741, Mailpit :8025)
**Actor**: Bob Ndlovu (Admin) — KC session on :3000.
**Tooling**: **Playwright MCP exclusively** (clean Chromium). DB reads via `docker exec b2b-postgres psql -d docteams`; Mailpit API for email verification.
**Context swap**: portal (Sipho) → firm (Bob).

---

## Balance note (scenario vs this cycle — NOT a defect)

The scenario expects **R 71,000** after Day 45, but its own amendment note (cycle 18) explains R71,000 = R50,000 (Day 10) + **R1,000 (Day 14 cycle-15 OBS-1101 verify carry-over)** + R20,000 (Day 45). **This cycle has NO R1,000 Day-14 deposit** — Day 14 of cycle 2026-06-13 recorded only Moroka's R25,000 (DEP/2026/002), no R1,000 Sipho top-up (that was a one-off OBS-1101 Mailpit-formatting verify in a prior cycle). DB confirms Sipho's only prior deposit is DEP/2026/001 R50,000. **Therefore this cycle's correct post-Day-45 Sipho balance is R 50,000 + R 20,000 = R 70,000.** The R71,000 figure is a prior-cycle artifact, not a regression.

---

## Day 45 checkpoints

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 45.1 | RAF-2026-001 → New Info Request "Supporting medical evidence", 2 items, due Day 52 → Send | **PASS** | Matter Client → Requests sub-tab → **New Request** (ad-hoc; contact pre-filled Sipho). 2 items: "Hospital discharge summary" + "Orthopaedic report" (both Required, File upload), Due 2026-07-20. **Send Now** → **REQ-0004** `ab11a55f-1491-4faf-9c6f-09536c6c8dc8` row shows **Sent / Sipho Dlamini / 0/2 accepted / 13 Jun 2026**. Backend: `Created ad-hoc information request ab11a55f-… (REQ-0004)` → `Sent information request … (REQ-0004)`, 0 ERROR. (Ad-hoc requests carry content via items; there is no free-text "title" field — the request gets the REQ number. REQ-0003 belongs to Moroka from Day 14, hence the new one is REQ-0004.) |
| 45.2 | Mailpit → second magic-link email to Sipho | **PASS** | Mailpit `NfdFk5sgvpGJ2XCVYQn8en` subject **"Information request REQ-0004 from Mathebula & Partners"** to sipho.portal@example.com @ 16:52:25. Body carries a fresh portal magic-link `:3002/auth/exchange?token=[REDACTED-MAGIC-LINK-TOKEN]&orgId=mathebula-partners`. |
| 45.3 | Trust Accounting → record R 20,000 deposit vs Sipho / RAF-2026-001, "Top-up per engagement letter" | **PASS** | Trust Accounting → Record Transaction → **Record Deposit**. Client combobox (OBS-1001 real-click picker) → **Sipho Dlamini**; Matter combobox (correctly scoped to Sipho's 2 matters) → **Dlamini v Road Accident Fund**; Amount **20000**, Reference DEP/2026/003, Description "Top-up per engagement letter". **Record Deposit** → transaction list shows **DEP/2026/003 / Deposit / R 20 000,00 / RECORDED** (posted directly, no dual-approval). DB: `trust_transactions` for Sipho = DEP/2026/001 R50k + DEP/2026/003 R20k (both RECORDED). |
| 45.4 | Client ledger trust balance = R 70,000 (cycle-adjusted from scenario R71,000 — see note) | **PASS** | Client Ledgers: **Sipho Dlamini — Trust Balance R 70 000,00 / Total Deposits R 70 000,00 / Payments R 0,00 / Fee Transfers R 0,00**. Moroka separate at R 25,000 (isolation holds — no aggregation). (Trust-account overview cashbook read R 75,000 BEFORE this deposit = R50k Sipho + R25k Moroka; after DEP/2026/003 the cashbook is R 95,000 = R70k Sipho + R25k Moroka.) |
| 45.5 | Matter Finance → Trust sub-tab shows R 70,000 | **PASS** | RAF-2026-001 Finance group → Trust sub-tab: **Funds Held R 70 000,00 / Deposits R 70 000,00 / Payments R 0,00 / Fee Transfers R 0,00 / Last transaction 2026/06/13**. Reconciles to client ledger. 📸 `day-45-matter-trust-balance.png`. |

---

## Day 45 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Second info request dispatched | **PASS** | REQ-0004 SENT to Sipho + magic-link email delivered (45.1 + 45.2). |
| Trust balance reconciles on client ledger + matter trust tab | **PASS** | Both surfaces = **R 70,000** (45.4 + 45.5); cycle-correct (R71,000 figure depends on a prior-cycle R1,000 deposit absent here). |

---

## Console / backend health
- **Firm console (matter Finance→Trust)**: only **OBS-201** `/api/assistant/invocations` 404s (exempt carry-over) — 0 genuine errors.
- **Backend log (16:52 window)**: **0 ERROR / 0 rollback** across info-request create+send and the deposit.

## Carry-over exemptions observed (noted, not re-filed)
- **OBS-201** — firm-side assistant 404 (KC proxy unwired) — exempt.
- **OBS-1101 R1,000 carry-over deposit** — not present this cycle; scenario's R71,000 vs observed R70,000 is the prior-cycle artifact described above, not a defect.

## New gaps
- **None.**

## Result
**Day 45: 5/5 step checkpoints PASS + 2/2 summary checkpoints PASS; 0 new gaps; NOT blocked.** Second info request (REQ-0004) dispatched with magic-link; second trust deposit (DEP/2026/003 R20,000) recorded; Sipho trust balance reconciles to **R 70,000** on both client ledger and matter trust tab. **Day 46 next** (Sipho responds to REQ-0004 on the portal + trust re-check + isolation spot-check).

Screenshots: `day-45-matter-trust-balance.png`.
