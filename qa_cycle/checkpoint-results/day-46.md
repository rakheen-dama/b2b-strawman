# Day 46 — Sipho responds to REQ-0003 + Trust re-check + Isolation spot-check (PORTAL)

**Branch**: `bugfix_cycle_2026-04-30b`
**Cycle**: 19 (2026-04-30)
**Actor**: Sipho Dlamini (portal `:3002`)
**Stack health (pre-test)**: backend 99563 / gateway 18539 / frontend 68198 / portal 18737 — RUNNING + HEALTHY.

## Result: PASS — Day 46 COMPLETE.

## Step 1 — Magic-link re-auth

Sipho's standing portal session expired between cycles. Re-requested magic-link via `/login` → entered `sipho.portal@example.com` → clicked Send Magic Link → dev-mode auto-link `?token=uiVMpcoRR-7UV646fpCk-KBMweuixE4dLDPlMQQSA1s&orgId=mathebula-partners` rendered → exchange → redirected to `/home`. User menu shows "Sipho Dlamini". Console clean.

## 46.2 — REQ-0003 detail surface: PASS

`/home` shows "Pending info requests: 1" → click → `/requests` → REQ-0003 row visible (status SENT, 0/2 submitted). Click into envelope → `/requests/1a02aaa4-85c6-45c9-8f08-b2bcf52caaa1` → detail page renders 2 required items:
1. **Hospital discharge summary** (required, File Upload)
2. **Orthopaedic specialist report** (required, File Upload)

Both items have `Choose file` button + disabled `Upload and submit` button. Header reads `0/2 submitted • status SENT`.

Evidence: `qa_cycle/evidence/day-46/req-0003-detail-before.png`.

## 46.3 — Upload + submit both PDFs: PASS

Used `qa_cycle/test-fixtures/test-doc.pdf` for both items.

**Item 1 (Hospital discharge summary)**:
- Click `Upload file for Hospital discharge summary` → file chooser opened → selected `test-doc.pdf` → file chosen state, Upload-and-submit button enabled.
- Click `Upload and submit` → item transitions to `Submitted — status: SUBMITTED`.
- Envelope counter updates: `1/2 submitted • status IN_PROGRESS`.

**Item 2 (Orthopaedic specialist report)**:
- Same flow: Upload file → select `test-doc.pdf` → Upload-and-submit → transitions to `Submitted — status: SUBMITTED`.
- Envelope counter updates: `2/2 submitted • status IN_PROGRESS`.

Note on envelope status: stays `IN_PROGRESS` after 2/2 items submitted (matches OBS-403 lifecycle — firm `Mark as Reviewed` / `Accept` is the next transition that closes the envelope to `COMPLETED`). This is by-design and was confirmed in Day 60 prep step 3 below where Bob accepted both items and the envelope auto-transitioned to `COMPLETED`.

Evidence: `qa_cycle/evidence/day-46/req-0003-both-submitted.png`.

## 46.4 — Trust balance R 71,000: PASS

Click sidebar `Trust` link → portal redirects to `/trust/b7e319f7-...` (single-matter contact, expected — see Day 15 portal isolation findings). Trust panel renders:
- **Funds Held**: R 71 000,00
- As of 30 Apr 2026
- Matter `b7e319f7`

(Scenario expected R 70k; actual R 71k carries the Day 14 R 1k OBS-1101 verify deposit forward — same amend documented at Day 45 retry.)

Evidence: `qa_cycle/evidence/day-46/portal-trust-71000.png`.

## 46.5 — Transaction list: PASS

Three deposit rows visible, descending by running balance:
| Date | Type | Description | Amount | Running balance |
|------|------|-------------|--------|-----------------|
| 30 Apr 2026 | DEPOSIT | Top-up per engagement letter | R 20 000,00 | R 71 000,00 |
| 30 Apr 2026 | DEPOSIT | Top-up trust deposit — RAF-2026-001 | R 1 000,00 | R 51 000,00 |
| 30 Apr 2026 | DEPOSIT | Initial trust deposit — RAF-2026-001 | R 50 000,00 | R 50 000,00 |

All 3 deposits visible; amounts and running-balance arithmetic correct.

## 46.6 — Isolation spot-check: PASS

Body-text scans on `/trust/{matterId}`, `/projects`, `/home`:
- `/trust/{matterId}`: 543 chars body. `/moroka/i` = false, `/EST-2026/i` = false, `/liquidation/i` = false, `/R 25 ?000/i` = false.
- `/projects`: 3 Sipho-only matters listed (Engagement Letter, OBS-301 Verify, Dlamini v RAF). Same 4 isolation regexes all false.
- `/home`: same regexes false; "Pending info requests" counter = 0.

Zero Moroka data leakage 32 days after Moroka onboarding. Tenant/customer isolation holds at portal level.

## 46.7 — Pending info requests dropped: PASS

`/home` "Pending info requests" card shows **0** after both items submitted (was 1 before). Card behaviour is correct — submitted items move out of pending.

Evidence: `qa_cycle/evidence/day-46/portal-home-after-submit.png`.

## Day 46 checkpoints

- [x] Second info request lifecycle complete (2/2 items submitted from portal)
- [x] Trust balance update visible on portal — R 71,000.00 (amended from scenario R 70k for Day 14 carry-over)
- [x] All deposits listed correctly with right running balances
- [x] Isolation holds — zero Moroka leakage 32 days after onboarding
- [x] Pending info-request counter drops to 0 after submission

## Console & Network

Console clean throughout (0 errors). Single benign `Next.js Dev Tools` notice (pre-existing harness noise).

## Scenario amendments

- 46.4 R 70,000 → **R 71,000** (carry-over Day 14 R 1,000 OBS-1101 verify deposit; same amend already applied at Day 45.4).
- 46.5 transaction list shows 3 rows, not 2 (because Day 14 OBS-1101 verify added a third deposit). Scenario expected "Day 10 R 50,000 + Day 45 R 20,000"; reality is "Day 10 R 50k + Day 14 R 1k + Day 45 R 20k". Order DESC by running-balance (newest first), all dates correct.
