# Fix-Verification Pass — QA Cycle 2026-07-06 — executed 2026-07-07/08

Stack: frontend :3000, backend :8080 (restarted post-merge), gateway :8443, portal :3002, Mailpit :8025.
Users: Thandi (owner, SecureP@ss1), Bob (admin, SecureP@ss2). Org: mathebula-partners.
Mailpit baseline at start: 28 messages. Backend log baseline: line 263 (pre-session), line 343 (pre-billing-run).

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

## Flow B — LZKC-005 deal-won email

(see below — executed after flow A)

## Flow C — LZKC-016 trust dual-approval feedback

Setup (Thandi, pre-staged): DEP/2026/004 R 70 000 deposit for Sipho → RECORDED; PAY/2026/002 R 70 000 payment → AWAITING APPROVAL (Approve/Reject buttons on row).

(see below — executed after flow A)

## Flow D — LZKC-018 closure letter

(see below)

## Console/log hygiene observed during pass
- Firm /dashboard still logs the known open LZKC-011 SVG sparkline error (not in this fix wave).
- Backend log 0 ERROR lines during billing flow.
