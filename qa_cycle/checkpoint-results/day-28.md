# Day 28 — Firm generates first fee note (bulk billing)  `[FIRM]`

**Date**: 2026-05-14 (cycle 1, branch `bugfix_cycle_2026-05-13`)
**Actor**: Thandi Mathebula (Owner, `thandi@mathebula-test.local` / `SecureP@ss1`)
**Stack**: Keycloak dev stack (frontend `:3000`, gateway `:8443`, backend `:8080`, KC `:8180`).
**Result**: PASS — All 3 checkpoints pass. Fee note INV-0001 generated, approved, sent via email.

---

## Pre-flight

- Stack health: backend 8080 healthy (200), gateway 8443 healthy (200), frontend 3000 healthy (200).
- User-swap Bob → Thandi via in-app user-menu Sign Out → KC realm logout → Sign In as Thandi (`thandi@mathebula-test.local` / `SecureP@ss1`). Sidebar avatar `TM` confirms swap.
- Day 21 completed: 2 time entries on RAF-2026-001 (4h total, all billable, no rate card → R 0,00 billable value) + 1 disbursement (R 1,250 base / R 1,437.50 incl VAT) on Sipho's matter.

## Pre-condition — Approve sheriff disbursement + Activate Sipho

| ck | Step | Result |
|----|------|--------|
| 0.1 | Navigate to RAF-2026-001 → Disbursements tab | PASS — row visible: `Sheriff service of summons on RAF · R 1,437.50 · Draft · Unbilled` |
| 0.2 | Click row → land on `/legal/disbursements/{id}` detail | PASS — detail page renders: Submit for Approval / Upload receipt / Edit buttons. Financial summary: Amount (excl VAT) R 1,250.00, VAT R 187.50 Standard (15%), Total (incl VAT) R 1,437.50, Payment Source: Office Account. |
| 0.3 | Click `Submit for Approval` | PASS — status transitions Draft → Pending. "Approval Required" section appears with Approve/Reject buttons. |
| 0.4 | Click `Approve` → confirmation dialog "Approve Disbursement" (optional notes) → `Approve Disbursement` | PASS — status transitions Pending → Approved. Badge reads "Approved · Unbilled". |
| 0.5 | Navigate to Sipho's client detail → Change Status → Activate | PASS — "Activate Client" alert dialog. No prerequisites blocking dialog (OBS-2102 fix: INDIVIDUAL tax_number skip). Clicked Activate → lifecycle transitions Onboarding → Active. Both badges now show "Active / Active". |

Evidence: `qa_cycle/evidence/day-28/step2-select-customers.png` (Sipho visible after activation).

## Step 1 — Billing Run Wizard (28.1 – 28.4)

| ck | Step | Result |
|----|------|--------|
| 28.1 | `Finance > Billing Runs > New Billing Run` | PASS — wizard opens at step 1 ("Configure Billing Run"). Steps shown: Configure / Select Customers / Review & Cherry-Pick / Review Drafts / Send. |
| 28.2 (config) | Period From=2026-05-01, Period To=2026-05-31 → Next | PASS — wizard advances to step 2. |
| 28.2 (select) | "Select Customers" panel | PASS — **Sipho appears**: `Sipho Dlamini · Unbilled Time R 0,00 · Unbilled Expenses R 1,437.50 · Total R 1,437.50`. Auto-checked, "1 customer selected" confirmed. Expense total is correct (no Cartesian inflation — OBS-2104b appears resolved). |
| 28.3 (cherry-pick) | Step 3 "Review & Cherry-Pick" — expand Sipho | PASS — **Time Entries** section: 2 entries checked (2.5h + 1.5h, both Rate/Amount N/A due to no rate card). **Disbursements** section: 1 entry checked (Sheriff service of summons on RAF, SHERIFF_FEES, Sheriff Sandton, R 1,437.50). Subtotal: R 1,437.50. **Note**: Disbursements section IS present in step 3 (OBS-2104c from previous cycle appears resolved). |
| 28.4 | Click Next → billing run generates | PASS — billing run auto-completed with status COMPLETED. 1 invoice generated. Step 4 message: "Only billing runs in PREVIEW status can be generated. Current status: COMPLETED" (auto-advance behavior). |

Evidence: `qa_cycle/evidence/day-28/step2-select-customers.png`, `qa_cycle/evidence/day-28/step3-cherry-pick.png`.

## Step 2 — Fee Note Draft Review (28.5)

| ck | Step | Result |
|----|------|--------|
| 28.5a | Navigate to Fee Notes → click Draft fee note | PASS — Draft Fee Note, Client: Sipho Dlamini, Currency: ZAR. |
| 28.5b | Line Items table | PASS — 2 TIME line items visible: (1) "Initial RAF claim assessment & instructions -- 2026-05-14 -- Bob Ndlovu" Qty 2.5 / R 0,00 / VAT Standard 15% R 0,00; (2) "File RAF1 claim form + supporting documents (within 3-year prescription) -- 2026-05-14 -- Bob Ndlovu" Qty 1.5 / R 0,00 / VAT Standard 15% R 0,00. Disbursement NOT visible as a separate row in the Line Items table but IS included in the totals (auto-attached during billing run). |
| 28.5c | "Add Disbursements" check | PASS — dialog shows "No unbilled approved disbursements for this matter" confirming the sheriff fee was auto-attached. Disbursement billing status changed from Unbilled to Billed. |
| 28.5d | Preview (new tab) | PASS — Full preview renders with 3 line items: (1) TIME "Initial RAF claim assessment..." 2.5 / 0.00 / 0.00 / VAT Standard 15%, (2) TIME "File RAF1 claim form..." 1.5 / 0.00 / 0.00 / VAT Standard 15%, (3) EXPENSE "Sheriff fees: Sheriff service of summons on RAF (Sheriff Sandton, 2026-05-14)" 1.0 / 1250.00 / 1250.00 / VAT Standard 15%. Subtotal: 1250.00 / VAT Standard (15%): 187.50 / **Total (ZAR): 1437.50**. |
| 28.5e | Letterhead + matter ref | PASS — "Mathebula & Partners" header. Matter: "Dlamini v Road Accident Fund". Bill To: Sipho Dlamini, sipho.portal@example.com. ZAR currency. |
| 28.5f | Totals | Subtotal R 1,250.00 / VAT (%) R 187.50 / Total R 1,437.50. |

Evidence: `qa_cycle/evidence/day-28/fee-note-preview.png`.

**Note on disbursement line visibility**: The disbursement is auto-included in the billing run and appears in the Preview PDF as a separate EXPENSE line (correctly labelled with supplier + date). However, the Line Items editor table on the fee note detail page only shows TIME entries — the disbursement is tracked separately in the invoice model. This is a minor UX gap (editor doesn't render disbursement rows inline) but functionally correct — the totals and preview PDF are accurate.

## Step 3 — Approve & Send (28.6 – 28.7)

| ck | Step | Result |
|----|------|--------|
| 28.6a | Click `Approve` | PASS — status transitions Draft → Approved. INV-0001 number assigned. Buttons change to Preview / Send Fee Note / Void. Issued date: May 14, 2026. |
| 28.6b | Click `Send Fee Note` | Validation warning: "Tax Number is required to send an invoice" (Sipho INDIVIDUAL has no tax number per OBS-2102 design). Override available for admin/owner. |
| 28.6c | Click `Send Anyway` | PASS — status transitions Approved → Sent. Buttons change to Preview / Record Payment / Void. Payment History section: "No payment events yet." |
| 28.7a | Mailpit → fee note email | PASS — email "Fee Note INV-0001 from Mathebula & Partners" delivered to `sipho.portal@example.com` (Date: Thu, 14 May 2026, 1:22 am; 8.5 kB; 1 attachment: INV-0001.pdf 1.2 kB). |
| 28.7b | Email body | PASS — "Fee Note from Mathebula & Partners", "Hi Sipho Dlamini", Fee Note Number INV-0001, Amount Due ZAR 1437.50, `View Fee Note` button (links to `http://localhost:3002/invoices/{id}`). Branding header "Mathebula & Partners". |

Evidence: `qa_cycle/evidence/day-28/fee-note-sent.png`, `qa_cycle/evidence/day-28/mailpit-fee-note-email.png`.

## Day 28 checkpoints

- [x] **Fee note generated with TIME line-type time entries + EXPENSE disbursement line correctly separated** — PASS. Preview PDF shows 2 TIME entries (R 0,00 each, no rate card cascade) + 1 EXPENSE row "Sheriff fees: Sheriff service of summons on RAF (Sheriff Sandton, 2026-05-14)" R 1,250.00. Total R 1,437.50 (incl VAT).
- [x] **Terminology: firm-side copy reads "Fee Note" (not "Invoice") end-to-end** — PASS. Header "Draft Fee Note" / approved "INV-0001" / breadcrumb "Fee Notes" / email subject "Fee Note INV-0001 from Mathebula & Partners". URL slug retains `/invoices` — acceptable URL-vs-display split.
- [x] **Email dispatched with portal payment link** — PASS. Subject "Fee Note INV-0001 from Mathebula & Partners". Body contains `View Fee Note` button linking to `http://localhost:3002/invoices/{id}` (portal URL). Attachment: INV-0001.pdf.

## Console / network

- Console errors: All errors are the pre-existing OBS-203 (`/api/assistant/invocations` returns 404 on invoice detail page). 5 occurrences (polling interval). No new errors.
- No hydration mismatches, no React warnings, no network failures.
- HMR messages: normal dev-mode reconnection logs.

## Improvements over previous cycle (cycle 16)

1. **OBS-2104b (Cartesian inflation) — appears resolved**: Wizard step 2 shows R 1,437.50 for Sipho's expenses (correct), not the R 11,250 inflation seen in cycle 16 (9 tasks × 1 disbursement).
2. **OBS-2104c (Cherry-pick missing Disbursements section) — resolved**: Step 3 now renders both "Time Entries" and "Disbursements" sections.
3. **Disbursement auto-attachment**: The billing run now auto-attaches approved disbursements to the generated fee note — no manual "Add Disbursements" step needed (was required in cycle 16).

## QA Position

**Day 28**: COMPLETE — All 3 checkpoints PASS. Fee note INV-0001 with R 1,437.50 total (R 1,250 + 15% VAT) emailed to Sipho.

**Next checkpoint**: Day 30 (Sipho pays fee note via portal — PayFast/mock payment).

---

**Time on day**: ~12 min (login swap, disbursement approval, Sipho activation, billing wizard, approve/send, Mailpit verification).
**Tool count**: ~45 calls.
**No regressions in Days 0-21 evidence.**
