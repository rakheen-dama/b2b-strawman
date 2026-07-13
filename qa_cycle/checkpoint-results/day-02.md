# Day 2 — Onboard Sipho as client, conflict check + KYC + pipeline enquiry `[FIRM]` — Cycle 2026-07-12

**Actor**: Bob Ndlovu (Admin). Context swap performed (cookies cleared, fresh KC login as bob@mathebula-test.local). Day 1 exit re-confirmed on this login: logo + `#1B3358` accent render in Bob's fresh session.

| Checkpoint | Result | Evidence |
|---|---|---|
| 2.1 Clients → New Client | PASS | /org/mathebula-partners/customers → "Create Client" dialog (2-step wizard) |
| 2.2 legal promoted fields for INDIVIDUAL | PASS | Step 2 "SA Legal — Client Details": ID / Passport Number, Postal Address, Preferred Correspondence, Referred By (same surface as prior cycle) |
| 2.3 fill client | PASS | Name "Sipho Dlamini" (single Name field — product design), Individual, sipho.portal@example.com, +27 82 555 0101, 12 Loveday St / Johannesburg / 2001 / South Africa (ZA), ID 8501015800088 |
| 2.4 submit → client detail | PASS | Redirected to `/customers/d0c7daf5-7085-4560-afb9-e9e937db5abc`; badges Active + Prospect |
| 2.5 Run Conflict Check | PASS | Client detail "More actions" → Run Conflict Check → /conflict-check pre-filled (`customerId`+`checkedName` params); ID 8501015800088 entered; check run |
| 2.6 result CLEAR | PASS | "No Conflict — Checked \"Sipho Dlamini\" at 12/07/2026, 22:45:14"; History (1) |
| 2.7 screenshot | PASS | `qa_cycle/checkpoint-results/day-02-conflict-check-clear.png` |
| 2.8–2.10 KYC verification | SKIPPED (mandate exemption) | No KYC adapter configured — client detail exposes only the AI "Verify with AI" FICA surface (needs uploaded docs + BYOAK). KYC integration = documented WONT_FIX exemption per cycle mandate; same state as prior cycle, not a new gap |
| 2.11 Pipeline board legal-za stages | PASS | Columns Enquiry / Conflict check / Engagement / Won / Lost; 0 console errors on load — prior-cycle LZKC-001 hydration-mismatch fix holds |
| 2.12 New Enquiry | PASS | Pick existing customer → Sipho Dlamini; Title "Dlamini v RAF — enquiry"; Value 87500; Source Referral → card in Enquiry "10% · R 8 750,00"; open weighted value R 8 750,00 |
| 2.13 drag Enquiry → Conflict check | PASS | dnd-kit drag with ring-2 column-identity oracle (first blind attempt dropped back into Enquiry — known drop-oracle behaviour, retried per protocol); card now "Conflict check 1 — 30% · R 26 250,00"; announcement "Draggable item 3aad1c89… dropped over droppable area 5466c24b…" |
| 2.14 client Work > Deals tab | PASS | `?tab=deals`: row "Dlamini v RAF — enquiry / DEAL-0001 / Conflict check / Open / R 87 500,00 / 12 Jul 2026" |
| 2.15 screenshot | PASS | `qa_cycle/checkpoint-results/day-02-pipeline-enquiry.png` |

## Day 2 exit checkpoints

- Client created INDIVIDUAL with legal-specific fields: PASS
- Conflict check CLEAR (no false positives): PASS
- KYC: not-configured state logged (mandate WONT_FIX exemption): DONE
- Pipeline enquiry DEAL-0001 open at Conflict check: PASS

## Gaps

- None new.

## Observations (non-blocking)

- Pipeline loaded clean this cycle (no `@dnd-kit/core` module error — prior cycle's stale node_modules infra issue not present; no hydration console error — LZKC-001 fix verified holding).
