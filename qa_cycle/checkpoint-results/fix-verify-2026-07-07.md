# Fix-Verification Pass — QA Cycle 2026-07-06 — executed 2026-07-07/08

Stack: frontend :3000, backend :8080 (restarted post-merge), gateway :8443, portal :3002, Mailpit :8025.
Users: Thandi (owner, SecureP@ss1), Bob (admin, SecureP@ss2). Org: mathebula-partners.
Mailpit baseline at start: 28 messages. Backend log baseline: line 263 (pre-session), line 343 (pre-billing-run).

## Verdicts (all 11 MERGED-AWAITING-VERIFY gaps)

| Gap | Verdict | Key evidence |
|-----|---------|--------------|
| LZKC-002 | VERIFIED | 2× fresh /proposals loads, zero console errors/hydration messages; create dialog works |
| LZKC-003 | VERIFIED | Dialog copy "Create an engagement letter for a client engagement." |
| LZKC-004 | VERIFIED | New client email subject "Fee Note INV-0002 from Mathebula & Partners" (fee-note-subject evidence per plan; forward-only) |
| LZKC-005 | VERIFIED | Mailpit `T2d4TLVShrPAc8j8jRRqJv` "You won a deal" → bob@ (email pref must be opted in — by design) |
| LZKC-006 | VERIFIED | Step 3→4→5 advanced; exactly 1 generate (requestId `2853aaeb…`); Back→Next re-entry clean |
| LZKC-008 | VERIFIED | INV-0002 sent with no CRITICAL block/"Send Anyway"; WARN tax_number_missing logged |
| LZKC-009 | VERIFIED | Preview header "Fee Note: INV-0002"; PDF "FEE NOTE"/"Fee Note Number"; sites 3/4 deferred as authorized |
| LZKC-012 | VERIFIED | Email attachment INV-0002.pdf = real line-item fee note; portal download byte-identical (MD5 `4c061a5e…`) |
| LZKC-016 | VERIFIED | Toast "Approval recorded — 1 of 2 approvals…", row indicator, "Approved by you" disabled; 2nd approve → APPROVED |
| LZKC-018 | VERIFIED | Closure-letter PDF: Date/Client/Matter/Reason/fees/disbursements/duration all populated |
| LZKC-021 | VERIFIED | My Work + Calendar say "matters"; "All Matters" filter; "Matters" tab |

## Flow A — Billing flow on ACTIVE Sipho matter (LZKC-006, 008, 009-cluster, 012)

Setup: Thandi recorded disbursement "Court filing fee — fix-verify QA" (Court Fees, R 800 excl VAT, zero-rated, Gauteng High Court) on matter `1c366c98-2e76-400f-8b9c-ce7b17cdb4e9` (Engagement Letter — Litigation (Dlamini v RAF)). Recorder's row menu has no approval action — approval lives at `/legal/disbursements/{id}`: Bob did Submit for Approval → Approve (dialog) → status Approved/Unbilled (id `b805eeb1-4457-4e6f-b1e7-42094d6c8f5d`).

### LZKC-006 — Billing wizard dead-end: **VERIFIED**
- Bob ran `/invoices/billing-runs/new`: step 1 (2026-07-01→31) → step 2 shows Sipho R 0 time / R 800 expenses → step 3 Review & Cherry-Pick → **Next advanced to step 4 Review Drafts** with draft INV-a40a6355 R 800,00 — NO "Only billing runs in PREVIEW status" dead-end.
- Backend log (lines >343): exactly **ONE** generation — single requestId `2853aaeb-b755-43f0-8091-60a477c5cd95` producing "Created draft invoice a40a6355…" + "Completed billing run b140b48b… — 1 invoices generated, 0 failed, total: 800.00". `grep -c 'Completed billing run b140b48b'` = 1 (re-checked after re-entry, still 1).
- Back→Next re-entry: step 4 re-rendered the same draft INV-a40a6355, no second generate call, no dead-end.
- Step 5 Send reached via Approve All & Continue (invoice approved as INV-0002).

### LZKC-008 — INDIVIDUAL tax-number send block: **VERIFIED**
- Send All → Confirm Send → "Billing Run Complete — 1 Sent, R 800,00". **No CRITICAL tax-number block, no "Send Anyway" step** for Sipho (INDIVIDUAL, no tax number).
- Warning downgraded severity observed in backend log 21:51:46Z WARN InvoiceCreationService "Draft invoice for customer f6f8050d… created without a tax number … (warning code: tax_number_missing)" — warning recorded, send proceeded. (No warning banner surfaced in the wizard batch-send UI; non-blocking is the fix criterion. Note: the WARN log message text still says "send will be blocked" — stale log copy, behaviour is not blocked.)
- Backend: "Marked invoice a40a6355… as sent" 21:53:38Z; "Batch send … 1 sent, 0 failed".

### Cluster fee-note copy (LZKC-009 sites) — **VERIFIED**
- Firm doc preview (`/api/invoices/{id}/preview`, tab title "Fee Note INV-0002") header: **"Fee Note: INV-0002"** (Day-28 evidence was "Invoice: DRAFT").
- Fee-note detail page: "Fee Note Details", "Back to Fee Notes" — legal-za nouns.
- Rendered PDF says "FEE NOTE" / "Fee Note Number: INV-0002" / "…settle this fee note by the due date".
- Validation copy site not directly triggerable (send passed validation cleanly); deferred sites 3/4 (template display name, audit facet) unchanged as authorized.
- Residual same-class strings observed (NOT in the fixed-site list, noted as observations): wizard "Edit Invoice" dialog title, "Invoice #" column header, "Ready to send: 1 invoices", Confirm Send "Send 1 invoices…", proposals stat card "No overdue proposals", My Work table column header "Project", invoice-detail help tooltip "Help: Invoice numbering".

### LZKC-012 — Client-facing fee-note PDF: **VERIFIED**
- Mailpit `ReGgTTA4C3GpcaKpCBi3ss` "Fee Note INV-0002 from Mathebula & Partners" → sipho.portal@example.com, attachment INV-0002.pdf (2 957 bytes, MD5 `4c061a5edae4964b9fd82018d0af3b67`).
- Attachment text extract: **FEE NOTE** header, From Mathebula & Partners, To Sipho Dlamini, Fee Note Number INV-0002, Issue Date 7 July 2026, line item "Court fees: Court filing fee — fix-verify QA (Gauteng High Court, 2026-07-07) | 1 | R800,00", Subtotal/VAT/Total Due R800,00, Payment Details + VAT Act s20 note — a REAL line-item fee note, not the blank cover letter (Day-30 artefact was 1 183 bytes with blank number/amount).
- Portal: fresh magic link (Mailpit `mcvJKQtzNAcu4B5GoHmM4m`) → `/invoices/a40a6355…` → "Download INV-0002 as PDF" → presigned `fee-note-inv-0002-2026-07-07.pdf` — **byte-identical to the email attachment (same MD5)**.
- Receipt/payment-confirmation artefact remains DEFERRED (epic) per orchestrator decision — not re-tested.

### LZKC-004 — client-facing email nouns: **VERIFIED (fee-note-subject evidence per plan)**
- New outbound client email uses legal-za nouns: subject "Fee Note INV-0002 from Mathebula & Partners" (vs Day-8 "New proposal PROP-0001…" class). No new engagement-letter send was minted this pass; per plan the new-email noun evidence stands. Forward-only fix — historical seeded bodies unchanged, as authorized.

## Flow E — LZKC-002 hydration on firm /proposals: **VERIFIED**
- Two fresh loads of `/org/mathebula-partners/proposals` as Thandi: console contains ONLY React-DevTools INFO + [HMR] connected — **zero errors, zero hydration/did-not-match messages** (grep over full console dump).
- Create dialog opens and functions post-mount ("New Engagement Letter" → dialog rendered with form).

## Flow F — Terminology cluster (LZKC-003 / LZKC-021): **VERIFIED**
- LZKC-003: create dialog description reads **"Create an engagement letter for a client engagement."** — article fixed ("a engagement" gone).
- LZKC-021: My Work subtitle **"Your action items and time tracking across all matters"**; Calendar subtitle **"View upcoming due dates across all matters"**, filter shows **"All Matters"**, toggle buttons **All / Action Items / Matters** — zero "projects" strings on either page (full-snapshot grep).

## Flow B — LZKC-005 deal-won email: **VERIFIED**

- Deal 1 (Bob, "QA fix-verify deal — LZKC-005", R 5 000, Sipho): dragged to Won (stepped mouse + "Mark as Won" confirm). Backend 22:03:33Z: "Transitioned deal f9ba9272… (WON)" + DealWonEventHandler "Post-commit DEAL_WON notification sent". **No email** — root cause found in code: `NotificationDispatcher.dispatch` only emails when the member's preference has email **explicitly enabled (default false)**; the DEAL_WON in-app toggle was on, email toggle off.
- Enabled Bob's DEAL_WON email toggle at `/settings/notifications` ("Preferences saved successfully"), then Deal 2 ("QA fix-verify deal 2 — LZKC-005 email", R 6 000) → Won at 22:07:23Z → **Mailpit `T2d4TLVShrPAc8j8jRRqJv` "You won a deal" → bob@mathebula-test.local**: "Hi Bob Ndlovu, A deal you own has been marked as won. View Deal (…)". DEAL_WON template mapping + notification-deal-won.html render correctly end-to-end.
- Fix verdict: VERIFIED — DEAL_WON emails now dispatch when the email channel is on; the "no email with default preferences" behaviour is the notification-preference system working as designed (all notification types email only when opted in), not a regression of PR #1515.
- Supporting observations: email deep link `http://localhost:3000/pipeline/{id}` lacks `/org/{slug}` → known **LZKC-022** (OPEN) confirmed live. WARN "Failed to generate unsubscribe URL … Unsubscribe functionality is not configured" logged per email (non-fatal, env config gap — unsubscribe secret unset in dev).

## Flow C — LZKC-016 trust dual-approval feedback: **VERIFIED**

Setup (Thandi): DEP/2026/004 R 70 000 deposit for Sipho → RECORDED; PAY/2026/002 R 70 000 payment → AWAITING APPROVAL.
- **First approve (Bob)** on `/trust-accounting/transactions?status=AWAITING_APPROVAL`: toast **"Approval recorded — 1 of 2 approvals. A second approver is required."**; row status cell gained **"1 of 2 approvals"** indicator under AWAITING APPROVAL; Bob's Approve button became **disabled "Approved by you"** (Reject still active). Screenshot: `fix-verify-lzkc016-first-approve.png`.
- Thandi (other viewer) also sees the server-rendered "1 of 2 approvals" indicator with an active Approve button.
- **Second approve (Thandi)**: toast **"Transaction approved"**, row → **APPROVED**.
- Ledger hygiene: deposit R 70 000 in + payment R 70 000 out → Sipho trust balance back to R 0 (no residue).
- Below-threshold single-approval path not re-exercised (kept ledger clean; the single-mode code path was covered by PR #1513 tests and is unchanged).

## Flow D — LZKC-018 closure letter: **VERIFIED**

- Scratch matter "QA Fix-Verify Closure Test — Litigation" (`96535fa6-22b3-4b5f-a411-08b1cc23b863`, template Litigation Personal Injury/General, client Sipho, ref QAV-2026-001) created and closed by Thandi via Close Matter → Concluded + notes + "Generate closure letter" (override used for the 2 amber gates: 9 open template tasks + no final bill; justification logged).
- Downloaded `matter-closure-letter-qa-fix-verify-closure-test-litigation-2026-07-08.pdf` (1.7 KB, matter Documents tab → presigned LocalStack URL). Text extract shows ALL template variables **populated** (Day-61 symptom was all blank): Date: **2026-07-07** (close date, UTC render; UI shows 8 Jul local — locale nuance, not blank), Client: **Sipho Dlamini**, Matter: **QA Fix-Verify Closure Test — Litigation** (hex-encoded text run), Reason for closure: **"Matter concluded"** + closure notes, Total fees billed: **0**, Total disbursements: **0**, Duration (months): **0** (true zeros for a same-day scratch matter — rendered values, not blanks).
- Confirmation of still-open gaps seen en route: closure history "Closed by 0768ccd3-…" raw UUID (**LZKC-014**, open, unchanged).

## Console/log hygiene observed during pass
- Firm /dashboard still logs the known open LZKC-011 SVG sparkline error (not in this fix wave).
- Backend log 0 ERROR lines during billing flow.
