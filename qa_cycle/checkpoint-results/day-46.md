# Day 46 — Sipho responds to second info request + trust re-check + isolation spot-check `[PORTAL]` — cycle 2026-07-12 (executed 2026-07-13)

**Actor**: Sipho Dlamini (:3002). Existing session from Day 30 magic link (00:35Z) still valid — no re-login needed (46.1 satisfied by the standing authenticated session; a fresh REQ-0003 magic link exists in Mailpit `9riaNx3n63YRmSVKw57HCC` had it been needed).

## Checkpoints

| # | Result | Evidence |
|---|--------|----------|
| 46.1 | PASS | Authenticated portal session (magic-link auth verified live on Day 30; REQ-0003 email link available) |
| 46.2 | PASS | `/home`: "Pending info requests 1"; `/requests` lists REQ-0003 SENT 0/2 → detail `/requests/9deb64e0-0154-4999-aace-7a87fc5b2e07` |
| 46.3 | PASS | Per-item Upload file → Upload and submit: hospital-discharge-summary.pdf then orthopaedic-report.pdf → both "Submitted — status: SUBMITTED", envelope "2/2 submitted • status IN_PROGRESS" (OBS-403 shape: envelope completes on firm acceptance, Day 60). 📸 `day-46-req0003-submitted.png` |
| 46.4 | PASS | `/trust` auto-forwards to Sipho's single active ledger: **Trust balance R 70 000,00** (cycle-correct; scripted R 71 000 includes a prior-cycle-only R 1 000 deposit — Day-45 amendment note) |
| 46.5 | PASS | Transactions newest-first with correct running balances: 13 Jul 2026 DEPOSIT "Top-up per engagement letter" R 20 000,00 → R 70 000,00; 12 Jul 2026 DEPOSIT "Initial trust deposit — RAF-2026-001" R 50 000,00 → R 50 000,00. Two deposits this cycle (scenario's three includes the phantom R 1 000). Client-safe descriptions, SA locale. 📸 `day-46-portal-trust-two-deposits.png` |
| 46.6 | PASS | Isolation: no Moroka R 25 000 anywhere on ledger/transactions; ledger scoped to matter 66451e87 (Sipho's) |
| 46.7 | PASS | `/home`: "Pending info requests 0" after submission |
| 46.8 | PASS | Screenshots as above |

## Day-level checkpoints

- Second info request lifecycle (client side) complete: **PASS** (firm acceptance leg belongs to Day 60)
- Trust balance update visible on portal (both deposits): **PASS**
- Isolation holds 31 scenario-days after explicit check: **PASS**

## New gaps

None.

## Observations

- Ledger card still titled "Matter 66451e87" (UUID prefix, not matter name) — pre-existing Day-11 observation, not re-filed.
- Console clean on all portal navigations (0 errors).

## Handoff for Day 60

- REQ-0003 `9deb64e0-0154-4999-aace-7a87fc5b2e07` — 2/2 SUBMITTED awaiting firm Accept ×2 (gate: info requests).
- 9 open template tasks on RAF-2026-001; Pre-Trial court date 2026-07-26 Scheduled (gate: court dates).
- Trust balance R 70 000 → Day-60 payment-out PAY/2026/001 R 70 000 requires dual approval (recorder ≠ sole approver; prior cycle needed TWO approvers for R 70 000).
- `task-completion-chain` automation: prior cycle it auto-spawned Follow-up tasks on Done (LZKC-013 was fixed as new-tenants-only + defaultEnabled=false honoured by seeder) — this tenant was re-provisioned in Session 0 AFTER the fix, so expect NO follow-up spawn; if one spawns, that's a regression of LZKC-013 (file new gap).
- INV-0001 PAID; final-bill gate should be green.
