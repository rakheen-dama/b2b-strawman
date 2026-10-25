# 625A / task 625.1 — reproduction evidence (G-B8, G-D9, G-D10, G-D8, N-6)

Run: 2026-10-24 23:32:57Z – 23:42Z (start ts in `start_ts`). Reproducer only — no source changed.

## Stack / build info
- main HEAD: `ea710c8cc` (worktree `/Users/rakheendama/Projects/2026/worktree-epic-625A` identical, clean).
- e2e-backend: image `docteams-e2e-backend` created `2026-10-24T19:38:48Z`, container created `22:50:39Z`. The tenant
  schema has Flyway V5 (`phase88 acceptance decline`) applied, so the image contains 624F. The later commits
  (624G/H/I and the docs commits) changed no backend files, so backend = main for this purpose.
- e2e-frontend :3001 (mock auth), e2e Mailpit is **:8026** (not :8025; :8025 is the KC Mailpit).
- Portal: KC portal stopped, worktree portal built + started with `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`
  (PID 53618) on :3002. Afterwards that PID was killed, the KC portal restarted (`svc.sh status`: `portal 59035 yes yes 3002`,
  cwd `/Users/rakheendama/Projects/2026/b2b-strawman/portal`).
- Tenant schema `tenant_7d218705360b`.

## Data used / created (all via the UI as Alice unless noted)
| Item | Id |
|---|---|
| Client Sipho Ndlovu mvmzw78f31c9 (ONBOARDING; portal contact `sipho.ndlovu.mvmzw78f31c9@example.com`, contact id `65f788e3-7d32-4125-ade0-1d25a5e0a85a`) | `1d7cae32-0deb-43fe-af41-a3b9452f3128` |
| New matter "Estate Late Ndlovu 625A repro" (Deceased Estate Administration template) | `9a27cd08-76fc-44b1-8c41-a8dd1aa1af22` |
| Trust deposit DEP/625A/001 R 50 000 (Alice) — status RECORDED | `b6d4deb2-c57b-4340-8ac4-6c784bc1852d` |
| Trust payment PAY/625A/001 R 2 500 (recorded Alice, approved **Bob**) — APPROVED | `ddeeffaa-bcc6-499b-8b91-56fc0155b1a6` |
| Engagement Letter — General, sent for acceptance, accepted on :3002 | acceptance cert under `.../certificates/e6f54be8-4696-4f4a-85a8-417c16a15679/` |
| Time entry 4h on task "Obtain death certificate & will" | (task `0c5af447-4a70-4442-a0b8-12eeebeeba7f`) |
| Fee note INV-0002 R 11 500 (approved, sent, part payment R 5 000 EFT/625A/001 → PARTIALLY_PAID) | `a93b0aca-402c-4148-adff-1d1cda0b7d6f` |
| Disposable matter "625A closure override repro" closed with override | `d9c61362-8b85-4bd5-bd46-96861173bc94`, closure log `383c83bb-7b78-4147-a072-7f375bb09fb0` |

## Step table
| # | Step | Expected (per spec) | Observed | Verdict |
|---|---|---|---|---|
| 1 | Matter Activity baseline | — | 1 item: "Alice Owner performed project.created_from_template on project" (raw literal) — `01` | — |
| 2 | Record R 50 000 deposit + approve | deposit visible, approve it | Deposit is created **RECORDED** with no Approve/Reject action (`02`). Code: `TrustTransactionService.recordDeposit` creates status `"RECORDED"` and bypasses approval; there is no deposit approval step, so `trust_deposit.approved` is never emitted. **Spec step doesn't match product.** To still exercise the approval path I recorded a R 2 500 trust PAYMENT (Alice) and approved it as Bob (single-approval mode: recorder cannot self-approve — "Self-approval not allowed", `approveSingleMode`). | SPEC MISMATCH (reported, see Surprises) |
| 3 | Matter Activity shows deposit / approval | absent (bug) | Neither `trust_deposit.recorded`, `trust_payment.recorded` nor `trust_payment.approved` appear (`05`). Audit rows lack `project_id` although `trust_transactions.project_id` = the matter. | **REPRODUCED** (G-B8 trust part) |
| 4 | Send + accept engagement letter → Matter Activity | absent (bug) | `acceptance.created/sent/viewed/accepted/certificate_generated` absent from matter Activity; none carry `project_id` (`03`, `05`). Only `document.generated` shows (it has `project_id`). | **REPRODUCED** (G-B8 acceptance part) |
| 5 | Create + approve + send fee note → Matter Activity | spec: none of them show | `invoice.created` and `invoice.approved` absent (no `project_id`). **`invoice.sent` DOES appear** ("Alice Owner performed invoice.sent on invoice") — `invoice.sent` details carry `project_id` (InvoiceTransitionService L246-262 derives it from lines). | PARTIAL: created/approved REPRODUCED; sent NOT REPRODUCED as "missing" — it shows but as a raw literal sentence |
| 5b | Record part payment → Matter Activity | (extra check) | `invoice.partially_paid` **appears** ("Alice Owner performed invoice.partially_paid on invoice"); has `project_id`. Raw literal (no formatter case). | Appears, raw sentence |
| 6 | Home → Recent Activity | "performed invoice.sent" | Sentences: "Alice Owner performed invoice.partially_paid", "Alice Owner performed invoice.sent", "Alice Owner created a time_entry", "Alice Owner performed document.generated", "Alice Owner performed project.created_from_template", plus older "performed matter_closure.closed / matter.closure.override_used / statement.generated / disbursement.billed / disbursement.approved" (`08`). Source: `DashboardService.buildActivityDescription` (L919-937) — only created/updated/deleted/uploaded get a verb, default `actor + " performed " + eventType`. Trust/acceptance/invoice.created/approved don't appear at all (query requires `project_id`). | **REPRODUCED** (G-D10) |
| 7 | Portal `/activity` Firm actions after approval (N-6) | approval shown? | Firm actions list (`07`): "Fee note sent to you", "Document generated for you", older SoA / Matter closed rows. **No trust approval row, no payment row.** "Your actions" tab: "No activity yet" (`06`) despite the contact viewing + accepting. | **N-6 CONFIRMED** (see below) |
| 8 | Close matter with override → `matter_closure.closed` details | justification missing | `matter_closure.closed` details = `{"reason":"CONCLUDED","actor_name":"Alice Owner","project_id":"d9c6…","override_used":true,"closure_log_id":"383c…"}` — **no justification, no notes**. The justification is present in the separate `matter.closure.override_used` row (entity_type `matter_closure`) and in `matter_closure_log.override_justification`. | **REPRODUCED** (G-D8) |

G-D9 (matter feed shows raw literals / misses events): matter feed query (`matter-feed-query.txt`, the exact
`AuditEventRepository.findByProjectId` SQL run for the estate matter) returns only 5 rows: `invoice.partially_paid`,
`invoice.sent`, `time_entry.created`, `document.generated`, `project.created_from_template`. Raw sentences come from
`ActivityMessageFormatter` default `"%s performed %s on %s"` (L153) — no cases for `invoice.*`, `trust_*`,
`acceptance.*`, `project.created_from_template`. **REPRODUCED.**

## Audit rows (full dump: `audit-rows.txt`)
| occurred_at (UTC) | event_type | entity_type | actor_type | project_id? | customer_id? |
|---|---|---|---|---|---|
| 23:33:26 | project.created_from_template | project | USER | YES | no |
| 23:34:40 | trust_deposit.recorded | trust_transaction | USER | **no** | YES |
| 23:35:06 | trust_payment.recorded | trust_transaction | USER | **no** | YES |
| 23:35:22 | trust_payment.approved | trust_transaction | USER (Bob) | **no** | **no** (details: amount, approved_by, transaction_type only) |
| 23:36:40 | document.generated | generated_document | USER | YES | no |
| 23:36:40 | document.generated_with_clauses | generated_document | USER | no | no |
| 23:36:57 | acceptance.created | acceptance_request | USER | **no** | YES |
| 23:36:57 | acceptance.sent | acceptance_request | USER | **no** | no |
| 23:37:16 | acceptance.viewed | acceptance_request | PORTAL_CONTACT (actor_id NULL) | **no** | no |
| 23:37:22 | acceptance.accepted | acceptance_request | PORTAL_CONTACT (actor_id NULL) | **no** | no |
| 23:37:22 | acceptance.certificate_generated | acceptance_request | SYSTEM | no | no |
| 23:38:24 | time_entry.created | time_entry | USER | YES | no |
| 23:38:51 | invoice.created | invoice | USER | **no** | YES |
| 23:39:09 | invoice.approved | invoice | USER | **no** | no |
| 23:39:17 | invoice.sent | invoice | USER | YES | no |
| 23:39:17 | document.generated (fee-note PDF) | generated_document | USER | no | no |
| 23:39:33 | invoice.partially_paid | invoice | USER | YES | no |
| 23:41:05 | project.created_from_template (closure matter) | project | USER | YES | no |
| 23:41:33 | matter_closure.closed | project | USER | YES | no — **no justification** |
| 23:41:33 | matter.closure.override_used | matter_closure | USER | YES | no — has `justification` |
| 23:41:33 | document.generated (closure letter) | generated_document | USER | YES | no |
| 23:41:33 | statement.generated | generated_document | USER | YES | no |

## N-6 verdict: CONFIRMED (and broader than hypothesised)
- Whitelist `PortalActivityEventTypes.PORTAL_VISIBLE_FIRM_EVENT_TYPES` (backend `portal/PortalActivityEventTypes.java`):
  `information_request.{created,sent,cancelled,completed,item_accepted,item_rejected,reminder_sent}`, `invoice.sent`,
  `invoice.payment_recorded`, `invoice.payment_reversed`, `invoice.payment_partially_reversed`,
  `trust_transaction.approved`, `trust_transaction.reversed`, `document.generated`, `docx_document.generated`,
  `statement.generated`, `matter_closure.closed`, `matter_closure.reopened`.
- Literal actually stored for the approval: **`trust_payment.approved`** (code: `"trust_" + type.toLowerCase() + ".approved"`,
  TrustTransactionService L900). `trust_transaction.approved` is only the Spring `TrustTransactionApprovalEvent.eventType()`
  (domain event), never an audit `event_type`. Tenant-wide counts: `trust_payment.approved` 2, no `trust_transaction.approved` rows.
- Second, independent blocker: `findActivityFirmForCustomer` requires `details->>'project_id'` IN the customer's projects;
  the approval row has no `project_id` (and no `customer_id`). Fixing the literal alone would still not surface it.
- Deposits are never approved at all (RECORDED path), so a deposit can never appear via an `.approved` event.
- Same defect class for payments: whitelist has `invoice.payment_recorded`, but the payment path emits
  `invoice.partially_paid` / `invoice.paid` (InvoiceTransitionService L418); `invoice.payment_recorded` is emitted nowhere in
  `backend/src/main` (grep). Observed: part payment not on portal Firm actions.
- Query re-run as SELECT: `portal-firm-feed-query.txt`.

## Surprises (reported, not worked around)
1. **Deposit has no approval step** — spec "record a R 50 000 deposit + approve" can't be executed; deposits are RECORDED
   immediately. Used an approvable trust PAYMENT for the approval half. Spec wording / N-6 hypothesis literal
   (`trust_deposit.approved`) needs correcting to `trust_payment.approved` / `trust_fee_transfer.approved` / `trust_refund.approved` etc.
2. **`invoice.sent` and `invoice.partially_paid` DO appear in matter Activity** (they carry `project_id`) — spec says "none of
   them". What's missing is `invoice.created` / `invoice.approved`; what shows renders as raw "performed invoice.sent on invoice".
3. **Portal "Your actions" is empty** after the contact viewed + accepted: the `acceptance.viewed/accepted` rows have
   `actor_type=PORTAL_CONTACT` but `actor_id` NULL, so `findActivityMineForPortalContact` (`actor_id = :portalContactId`)
   can't match. Out of 625A scope as written — flag for routing.
4. `invoice.payment_recorded` whitelist entry is dead (see N-6).
5. `project.created_from_template` renders raw in matter Activity ("performed project.created_from_template on project").
6. e2e Mailpit is on :8026 (brief said :8025).

## Screenshots
`01-matter-activity-baseline.png`, `02-deposit-recorded-no-approve-action.png`, `03-portal-acceptance-accepted.png`,
`04-fee-note-part-paid.png`, `05-matter-activity-after-actions.png`, `06-portal-activity-your-actions.png`,
`07-portal-activity-firm-actions-no-trust-approval.png`, `08-home-recent-activity-raw-literals.png`,
`09-closure-matter-activity.png` (originals also under `b2b-strawman/.playwright-mcp/625A/`).
