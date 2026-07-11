# Test Plan: Collections & Cash Intelligence Lifecycle
## Phase 83 — Overdue-Invoice Reminders, Escalation, Payment Cancellation & Weekly Cash Digest End-to-End

**Version**: 1.0
**Date**: 2026-07-11
**Author**: Product + QA (Epic 594 QA Capstone)
**Vertical**: accounting-za (e2e-test-org)
**Stack**: E2E mock-auth stack (frontend 3001 / backend 8081 / mock IDP 8090 / Postgres 5433 / Mailpit UI+API 8026 / LocalStack 4567)
**Method**: Browser-driven (Playwright MCP). Every checkpoint is observed, not inferred.
**Depends on**: Epics 588–593 merged; live Anthropic key (BYOAK) for AI-dependent checkpoints

---

## 1. Purpose

This plan proves the **complete collections & cash-intelligence loop** with observed evidence, per the
project's PASS-means-observed bar: enable the collections policy → seed overdue invoices via product
flows → run the scan → observe the AI-drafted reminder queue with correct stages → reject one, batch-approve
the rest → observe reminder emails in Mailpit (payment CTA present) → record a payment and observe the
pending gate cancelled in the UI and ledger → observe the ≥60-day escalation (bell + audit) → run the
weekly cash digest (email + bell, AI narrative) → turn AI off and observe the numbers-only digest → verify
the exempt customer produced nothing.

**Core question**: Can a firm owner enable collections, trust the automated reminder ladder, and rely on
the weekly cash digest — with every state transition observable in the browser, Mailpit, and the audit ledger?

## 2. Scope

| Track | Focus | Checkpoints |
|-------|-------|-------------|
| Setup | Stack up, login, policy on, BYOAK+profile, customers, invoices | CP-00 → CP-05 |
| Scan | Trigger scan, queue drafts, escalation | CP-06 → CP-08 |
| Approve/Email | Reject one, batch approve, reminder emails, sent ledger | CP-09 → CP-11 |
| Payment | Record payment, gate cancellation, re-approve refusal | CP-12 |
| Digest | Cash digest AI-on, numbers-only AI-off | CP-13 → CP-14 |
| Exemption | Exempt customer produced nothing | CP-15 |
| Gates | Regression gates + gap report | CP-16 |

## 3. Authorized Deviations & Reality-Checks (READ FIRST)

This script executes against the **e2e Docker stack**, where four premises from the Epic 594 task text
do not hold. Each is either an authorized deviation (recorded here per quality gate §6) or a documented
gap-report item. None was silently improvised.

### 3.1 Authorized deviations (granted by the user)

1. **`job_queue` INSERT as the scan/digest trigger** (CP-06, CP-09 re-scan, CP-13, CP-14).
   There is **no on-demand product trigger** for `collections_scan` or `cash_digest` — only cron fan-outs
   (daily 06:00 / Monday 07:00). `JobQueueAdminController` lists/retries/deletes only; no enqueue.
   Authorized fallback: a direct `INSERT INTO public.job_queue (...)` which the 2 s `JobWorker` poll then
   claims. This is a **job trigger, not data seeding** — it is flagged in every checkpoint that uses it and
   in the evidence log. The missing product trigger is filed as **GAP-P83-001**.

2. **Manual "Record Payment" instead of a webhook-sim payment** (CP-12).
   There is **no webhook payment simulator** on the e2e stack (`MockPaymentController`/`MockPaymentGateway`
   exclude the e2e profile; the real Stripe webhook verifies an unforgeable `Stripe-Signature`). Authorized
   deviation: use the manual payment route (UI "Record Payment" / `POST /api/invoices/{id}/payment`), which
   publishes the **same `InvoicePaidEvent`** consumed by the **same `CollectionsPaymentListener`**.
   Route-equivalence across manual/webhook/Xero payment routes is already proven by
   `CollectionsPaymentCancellationTest` (589B.2) inside `./mvnw verify`.

3. **Live Anthropic key via the BYOAK UI** (CP-03).
   There is **no stub AI provider** on the e2e image (`StubAiProvider` is test-JVM only; e2e resolves AI to
   the `noop` provider). A live key was entered **once, only through the Integrations UI "Set API Key"
   dialog**, never written to any file/log/command line, and masked in all evidence. The stub-parity gap is
   filed as **GAP-P83-002**.

### 3.2 Reality-checks (documented profile/environment limitations, not bugs)

- **No stub AI on e2e** → AI-dependent checkpoints require the live key; without it they are DEFERRED.
- **No product scan/digest trigger** → `job_queue` INSERT is the only trigger (GAP-P83-001).
- **No webhook simulator on e2e** → manual "Record Payment" deviation (route-equivalence test-proven).
- **accounting-za profile** → no trust-balance customer; the optional "customer with trust balance" step
  and the `TRUST_FUNDS_AVAILABLE` badge are **unobservable**. The trust step is dropped and logged as a
  documented profile limitation (GAP-P83-003), not a bug.
- **No clock override** (`Clock.systemDefaultZone()`) → backdated `dueDate` on SENT invoices is the sole
  overdue mechanism (validated as accepted — `dueDate` has no past-date validation).
- **Mailpit on e2e is port 8026** (SMTP 1026), not 8025.

---

## 4. Notation

Each checkpoint is a browser-observed action + verification. Screenshots captured at key moments into
`qa/testplan/artifacts/phase83/cp-NN-<slug>.png`.

- [ ] **PASS** — observed correct behaviour in the browser / Mailpit / ledger
- [ ] **FAIL** — incorrect behaviour observed (→ GAP-P83-NNN entry)
- [ ] **BLOCKED** — cannot proceed due to prerequisite failure
- [ ] **DEFERRED** — not executed this cycle (e.g. AI-dependent without a key); logged, not claimed as PASS
- [ ] **SCREENSHOT** — screenshot captured for evidence

**Live-AI assertions are shape-based** (subject references the invoice number; CTA href exists; narrative
block exists) — never exact-text, because AI output is non-deterministic.

---

## 5. CP-00 — Stack up + reseed

**Actor**: n/a (infra). **Precondition**: E2E stack already up and freshly seeded (per orchestrator).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-00.1 | `curl -sf http://localhost:8081/actuator/health` | `{"status":"UP"}` | [ ] |
| CP-00.2 | `curl -sf -o /dev/null -w "%{http_code}" http://localhost:3001` | `200` | [ ] |
| CP-00.3 | `curl -sf http://localhost:8026/api/v1/messages` | Mailpit API responds (200) | [ ] |
| CP-00.4 | Confirm seed state | org `e2e-test-org` (accounting-za, pro); Alice/Bob/Carol; ONE customer "Acme Corp" (ACTIVE); project "Website Redesign"; no invoices, no AI config, no firm profile | [ ] |

**Status: [x] PASS**

**Evidence log (CP-00):**
- Backend health: `curl http://localhost:8081/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`.
- Frontend 3001 → HTTP 200. Mailpit `http://localhost:8026/api/v1/messages` → HTTP 200.
- Org mapping (flagged read-only psql): `external_org_id=e2e-test-org → schema_name=tenant_7d218705360b`.
- **Seed-state discrepancy (documented, not a product bug):** the brief's CP-00 expected ONE seeded customer "Acme Corp (ACTIVE)". The freshly-seeded DB contained **zero** customers (`tenant_7d218705360b.customers` empty). Acme Corp was therefore created via the sanctioned product-API path in CP-04 alongside Beta/Gamma. Logged as **GAP-P83-004** (seed/environment, minor).

## 6. CP-01 — Mock login

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-01.1 | Navigate `http://localhost:3001/mock-login` | Mock-login page loads | [ ] |
| CP-01.2 | Click "Sign In" (defaults to Alice, owner) | Redirected to dashboard | [ ] |

**Evidence**: `cp-01-dashboard.png`.

**Status: [x] PASS**

**Evidence log (CP-01):**
- Navigated `/mock-login`, Alice (Owner) selected by default, clicked "Sign In" → redirected to `/org/e2e-test-org/dashboard`. User chip "Alice Owner / alice@e2e-test.local". Screenshot `cp-01-dashboard.png`.

## 7. CP-02 — Enable collections policy (settings UI)

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-02.1 | Navigate `/org/e2e-test-org/settings/collections` | Page h1 "Collections"; card h2 "Overdue-Invoice Reminders" | [ ] |
| CP-02.2 | Toggle "Enable collections reminders" ON | Toggle on | [ ] |
| CP-02.3 | Verify defaults 7/21/45/60 in Stage 1/2/3 + Escalation fields | Fields show 7, 21, 45, 60 | [ ] |
| CP-02.4 | Click "Save Settings" | Success message "Collections settings updated." | [ ] |
| CP-02.5 | (Optional negative) set Stage 2 = 5 → Save | Client error "Thresholds must strictly increase: stage 1 < stage 2 < stage 3 < escalation." → restore 21 | [ ] |

**Evidence**: `cp-02-collections-policy-enabled.png`.

**Status: [x] PASS** (negative sub-check CP-02.5 not executed — optional)

**Evidence log (CP-02):**
- Page h1 "Collections", card h2 "Overdue-Invoice Reminders" observed. Default fields 7 / 21 / 45 / 60 confirmed in Stage 1/2/3 + Escalation spinbuttons.
- Toggled "Enable collections reminders" ON, clicked "Save Settings" → success message "Collections settings updated." observed. Screenshot `cp-02-collections-policy-enabled.png`.

## 7a. CP-03 — Configure AI (BYOAK) + firm profile  [REQUIRES live Anthropic key]

**Actor**: Alice (owner). **Deviation**: live key entered via UI dialog only (see §3.1.3). Key masked in all evidence.

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-03.1 | Navigate `/org/e2e-test-org/settings/integrations` → "AI Assistant" card | Card "Enable AI-powered document drafting and analysis" | [ ] |
| CP-03.2 | Click "Set API Key" → enter `sk-ant-…` (via dialog only) → save | Key accepted, masked | [ ] |
| CP-03.3 | Toggle "Enabled" ON | Status badge "Active" | [ ] |
| CP-03.4 | (Optional) "Test Connection" | Connection OK | [ ] |
| CP-03.5 | Navigate `/org/e2e-test-org/settings/ai` ("AI Configuration") → create/save firm profile | Profile saved (required: `AiReminderComposer` throws `ai_unavailable` when firm-profile count == 0) | [ ] |

**Fallback if no key**: mark CP-03 + all AI-dependent checkpoints (CP-07 drafts, CP-09/10/11/12, CP-13 narrative) DEFERRED; run the deterministic remainder (scan → `SKIPPED(ai_unavailable)` rows; escalation, exemption, numbers-only digest still work); file GAP-P83-002.

**Evidence**: `cp-03-ai-active.png` (status badge only, screenshot taken AFTER the dialog closed; key masked).

**Status: [x] PASS**

**Evidence log (CP-03):**
- Integrations → AI Assistant card: selected Provider "anthropic" from the combobox (options were `noop` / `anthropic`).
- Clicked "Set API Key", typed the live key **into the dialog textbox only** (sanctioned path), clicked "Save Key". Dialog closed; key rendered masked as `••••nWbAAA`. The key was never written to any file, log, or command line.
- Toggled "Enabled" ON → status badge changed to **"Active"**; Model combobox + "Test Connection" button appeared. Screenshot `cp-03-ai-active.png` taken after dialog closed (badge only).
- Firm profile at `/settings/ai` ("Set Up AI"): added practice area "Commercial Law", selected Province "Gauteng" (defaults Conservative risk + Claude Sonnet 4.6), clicked "Complete Setup". Verified via flagged read-only psql: `tenant_7d218705360b.ai_firm_profiles` count = **2** (a double-submit created two rows; `AiReminderComposer` only requires count > 0, so AI drafting is enabled). Filed as **GAP-P83-005** (possible UI double-submit defect — unreproduced).

## 8. CP-04 — Customers: create 2, exempt 1

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-04.1 | Create "Beta Ltd" (`billing@beta.example.com`) via Customers UI → transition to ACTIVE | Beta Ltd ACTIVE | [ ] |
| CP-04.2 | Create "Gamma Corp" (`accounts@gamma.example.com`) via Customers UI → transition to ACTIVE | Gamma Corp ACTIVE | [ ] |
| CP-04.3 | Gamma detail (Details tab) → Collections card → toggle "Exclude from collections" ON | Helper "Exempt customers never receive automated payment reminders."; toggle on | [ ] |

**Evidence**: `cp-04-gamma-exempt.png`.

**Status: [x] PASS**

**Evidence log (CP-04):**
- Customers created via the sanctioned product-API path (seeder precedent — Acme absent from seed, so all three created this way): Beta Ltd `6b989213-c9c1-43cf-82bc-76a8ba31ae5d`, Gamma Corp `adc0f06d-5347-436e-865d-983532cfed0e`, Acme Corp `a0705a9f-0273-48fd-b8c8-f66fadc5af1e`. All transitioned PROSPECT → ONBOARDING → ACTIVE (activation required addressLine1/city/country, which were supplied). Verified lifecycle=ACTIVE for all three.
- CP-04.3 exemption toggle observed **in the browser**: Gamma detail (`?tab=details`) → Collections card with helper text "Exempt customers never receive automated payment reminders." → toggled "Exclude from collections" ON (`aria-checked` false → true). Persistence confirmed via API: `GET /api/customers/{gamma}` → `collectionsExempt=true`. Screenshot `cp-04-gamma-exempt.png`.

## 9. CP-05 — Invoices SENT with staggered (backdated) due dates

**Actor**: Alice (owner). Scan derives overdue from `status='SENT' AND due_date < CURRENT_DATE`; backdated due dates are the mechanism (accepted — no past-date validation).

Invoice matrix (relative to execution day; thresholds 7/21/45/60):

| Invoice | Customer | Due date | Expected scan outcome |
|---|---|---|---|
| INV-A1 | Acme | today − 10d | STAGE_1 PROPOSED (gate) |
| INV-A2 | Acme | today − 70d | STAGE_3 PROPOSED (gate) + STAGE_1/2 SKIPPED(superseded) + ESCALATION FLAGGED |
| INV-B1 | Beta | today − 25d | STAGE_2 PROPOSED (gate) + STAGE_1 SKIPPED — rejected in CP-09 |
| INV-B2 | Beta | today − 10d | STAGE_1 PROPOSED (gate) — left pending, then paid in CP-12 |
| INV-C1 | Gamma (exempt) | today − 30d | nothing — zero activity rows |

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-05.1 | Create INV-A1 (Acme, due −10d), add ≥1 line, Approve, Send | INV-A1 SENT | [ ] |
| CP-05.2 | Create INV-A2 (Acme, due −70d), line, Approve, Send | INV-A2 SENT | [ ] |
| CP-05.3 | Create INV-B1 (Beta, due −25d), line, Approve, Send | INV-B1 SENT | [ ] |
| CP-05.4 | Create INV-B2 (Beta, due −10d), line, Approve, Send | INV-B2 SENT | [ ] |
| CP-05.5 | Create INV-C1 (Gamma, due −30d), line, Approve, Send | INV-C1 SENT | [ ] |

Note: send-time CRITICAL validations (branding/customer fields) may 422 — use admin "Send Anyway" override or fill fields.

**Evidence**: `cp-05-invoices-sent.png` (5 SENT invoices with backdated due dates).

**Status: [x] PASS**

**Evidence log (CP-05):**
- Created via the sanctioned invoice-API path (create → add line → approve → send with `overrideWarnings:true`), backdated due dates accepted (no past-date validation). Invoice-number map:

  | Label | Number | Invoice ID | Customer | Due date | approve/send HTTP | Status |
  |---|---|---|---|---|---|---|
  | INV-A1 | INV-0001 | ab76915f-cd0a-4350-ba04-f1d0d43b41ac | Acme | 2026-07-01 (−10d) | 200 / 200 | SENT |
  | INV-A2 | INV-0002 | 74f672f8-5c54-41b6-8433-cb9ed6548282 | Acme | 2026-05-02 (−70d) | 200 / 200 | SENT |
  | INV-B1 | INV-0003 | a5e801f9-7edd-4ba5-a6bf-0b5fdc199ce4 | Beta | 2026-06-16 (−25d) | 200 / 200 | SENT |
  | INV-B2 | INV-0004 | b0ac2ff2-a46e-4987-aef1-e31f31c7d049 | Beta | 2026-07-01 (−10d) | 200 / 200 | SENT |
  | INV-C1 | INV-0005 | 2e6d10fb-87db-46c1-97f8-7c11e3f86224 | Gamma (exempt) | 2026-06-11 (−30d) | 200 / 200 | SENT |

- Invoice list UI (`/org/e2e-test-org/invoices`) shows all 5 rows "Sent" with the backdated due dates. Screenshot `cp-05-invoices-sent.png`. Send-time CRITICAL validations were overridden via `overrideWarnings:true` (equivalent to the admin "Send Anyway" dialog).

## 10. CP-06 — Trigger `collections_scan`  [AUTHORIZED job trigger — see §3.1.1]

**Actor**: system (JobWorker). **FLAGGED**: authorized `job_queue` INSERT (job trigger, not seeding).

```sql
-- psql -h localhost -p 5433 -U postgres -d app   (password: changeme)
INSERT INTO public.job_queue (job_type, tenant_id, org_id)
SELECT 'collections_scan', schema_name, org_id
FROM public.org_schema_mapping WHERE org_id = 'e2e-test-org';
```

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-06.1 | Execute the authorized INSERT (flagged) | Row enqueued PENDING | [ ] |
| CP-06.2 | Poll `SELECT job_type,status,error_message FROM public.job_queue ORDER BY created_at DESC LIMIT 5;` (read-only, flagged), up to ~90 s | `collections_scan` row → COMPLETED | [ ] |
| CP-06.3 | Backend log excerpt: `docker compose -f compose/docker-compose.e2e.yml logs backend \| grep -i collections` | scan ran | [ ] |

**Evidence**: job row COMPLETED + backend log excerpt.

**Status: [x] PASS** — AUTHORIZED job trigger used (flagged).

**Evidence log (CP-06):**
- **[FLAGGED job trigger]** `INSERT INTO public.job_queue (job_type, tenant_id, org_id) VALUES ('collections_scan','tenant_7d218705360b','e2e-test-org');` → `INSERT 0 1`.
- Polled job row (flagged read-only psql): CLAIMED for ~45 s (live AI drafting), then **COMPLETED** (~48 s total).
- Backend log: `CollectionsScanService: scan complete — proposed=4, skipped=0, escalated=1, superseded=3` (jobId 6ead81d5..., jobType collections_scan, tenant tenant_7d218705360b).
- Activity rows created (flagged read-only psql) exactly match the matrix: INV-0001 STAGE_1 PROPOSED(10d); INV-0002 STAGE_3 PROPOSED(70d)+STAGE_1/2 SKIPPED(superseded_by_higher_stage)+ESCALATION FLAGGED; INV-0003 STAGE_2 PROPOSED(25d)+STAGE_1 SKIPPED; INV-0004 STAGE_1 PROPOSED(10d); **INV-0005 (Gamma, exempt) → zero rows.**

## 11. CP-07 — Queue shows drafts with correct stages

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-07.1 | Navigate `/org/e2e-test-org/invoices/collections` | Page h1 "Collections" | [ ] |
| CP-07.2 | Debtor book: Acme row Signals | "Escalated" (destructive) badge | [ ] |
| CP-07.3 | Debtor book: Gamma row | "Exempt" badge | [ ] |
| CP-07.4 | Pending reminders section | "4 reminder(s) awaiting approval" (A1, A2, B1, B2) | [ ] |
| CP-07.5 | Expand each card (chevron) | Subject + Message preview of the real draft | [ ] |
| CP-07.6 | INV-A2 detail → "Collection activity" | STAGE_3 PROPOSED + STAGE_1/2 SKIPPED(`superseded_by_higher_stage`) + ESCALATION FLAGGED | [ ] |

**Evidence**: `cp-07-queue-4-drafts.png` (single full-page capture — shows BOTH the debtor-book Signals badges and the pending-reminders queue), `cp-07-stage3-draft-expanded.png`, `cp-07-inv-a2-ledger.png`.

**Status: [x] PASS**

**Evidence log (CP-07):**
- Page h1 "Collections". Debtor book: **Beta Ltd** (2 invoices, R3 450, 25d overdue), **Acme Corp** (2 invoices, 70d overdue, Signals **"Escalated"**), **Gamma Corp** (1 invoice, 30d overdue, Signals **"Exempt"**, "No chase history").
- Pending reminders: **"4 reminders awaiting approval"** with correct stages (live-AI subjects, shape-based — each references its invoice number):
  - INV-0004 "Friendly Reminder: INV-0004 – Payment Due" — **Stage 1**
  - INV-0001 "Friendly Reminder: Invoice INV-0001 – Payment Due" — **Stage 1**
  - INV-0003 "Payment Reminder – INV-0003 Now Overdue" — **Stage 2**
  - INV-0002 "Final Reminder: Outstanding Payment Required — INV-0002" — **Stage 3**
- Expanded Stage 3 (INV-0002) card: real AI narrative referencing invoice INV-0002 + "payment link provided below" + "Acme Corp"; gate "Send Collection Reminder" **PENDING**, **"2d 23h remaining"** (~72 h expiry). Approve/Reject buttons + AI-reasoning block present. `cp-07-stage3-draft-expanded.png`.
- INV-0002 detail "Collection activity": Stage 3 Proposed (Gate: Review) + Stage 2 Skipped (Superseded By Higher Stage) + Stage 1 Skipped (Superseded By Higher Stage) + Escalation Flagged (Escalated), all 70d overdue. `cp-07-inv-a2-ledger.png`.

## 12. CP-08 — Escalation: bell + audit

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-08.1 | Alice → bell (`aria-label="Notifications"`) | Text "Invoice {A2-number} is 70 days overdue — flagged for a partner call" | [ ] |
| CP-08.2 | Log in as Carol (member) → bell | Does NOT contain the escalation notification | [ ] |
| CP-08.3 | Audit `GET /api/audit-events?eventType=collections.escalation.flagged` | One row, entityType `collection_activity`, details `{invoice_id, invoice_number, days_overdue}` | [ ] |

**Evidence**: `cp-08-escalation-bell.png`, `cp-08-carol-no-escalation.png` + audit JSON excerpt.

**Status: [x] PASS**

**Evidence log (CP-08):**
- Alice notifications page: **"Invoice INV-0002 is 70 days overdue — flagged for a partner call"** present. `cp-08-escalation-bell.png`.
- Carol (member) — logged in via `/mock-login` (confirmed chip `carol@e2e-test.local`): notifications page shows **"You're all caught up"**, NO escalation notification. `cp-08-carol-no-escalation.png`.
- Audit `GET /api/audit-events?eventType=collections.escalation.flagged` → one row: `{"eventType":"collections.escalation.flagged","entityType":"collection_activity","details":{"actor_name":"System","invoice_id":"74f672f8-...","days_overdue":"70","invoice_number":"INV-0002"}}`.

## 13. CP-09 — Reject one draft (terminal per stage)

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-09.1 | B1 queue card → expand → "Reject" → "Reject Gate Action" dialog → confirm | Gate rejected | [ ] |
| CP-09.2 | B1 invoice detail "Collection activity" | STAGE_2 row status REJECTED | [ ] |
| CP-09.3 | Re-trigger scan (CP-06 SQL again — FLAGGED) | B1 STAGE_2 stays REJECTED; no new gate; B1 queue count unchanged | [ ] |

**Evidence**: `cp-09-b1-rejected.png`.

**Status: [x] PASS**

**Evidence log (CP-09):**
- Expanded INV-0003 (Beta, Stage 2) card → "Reject" → "Reject Gate Action" dialog (reason "QA capstone CP-09: reject terminal-per-stage test") → confirmed. Pending count dropped **4 → "3 reminders awaiting approval"**.
- INV-0003 detail "Collection activity" → **Stage 2 Rejected** + Stage 1 Skipped. `cp-09-b1-rejected.png`. DB confirm: STAGE_2=REJECTED, STAGE_1=SKIPPED.
- **[FLAGGED job trigger]** re-enqueued `collections_scan` → COMPLETED (no new drafting). After re-scan B1 unchanged (STAGE_2 REJECTED, no new gate); total `collection_activities` count still 8. Reject is terminal per stage.

## 14. CP-10 — Batch approve the rest → Mailpit

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-10.1 | Clear Mailpit: `curl -X DELETE http://localhost:8026/api/v1/messages` | Mailpit empty | [ ] |
| CP-10.2 | Select A1 + A2 (leave B2 unselected) → "Approve selected (2)" | "Last batch result": 2 × "Approved" | [ ] |
| CP-10.3 | Mailpit: 2 messages to `contact@acme.example.com` | Subject references invoice number (shape); body has facts table (invoice number, amount, due date) + payment CTA href | [ ] |
| CP-10.4 | `curl "http://localhost:8026/api/v1/search?query=to%3Acontact%40acme.example.com"` → record IDs; `GET /api/v1/message/{id}` | Message IDs recorded; CTA href present (portal-URL fallback — no PSP domain asserted) | [ ] |

**Evidence**: Mailpit message IDs + `cp-10-batch-approved.png`.

**Status: [x] PASS**

**Evidence log (CP-10):**
- Mailpit cleared (`DELETE /api/v1/messages` → total 0). Selected INV-0001 + INV-0002 (B2/INV-0004 left unselected), clicked "Approve selected (2)".
- "Last batch result": **2 × "Approved"** (Reminder ce40ef1e, Reminder 0cd3a45d). Pending dropped to "1 reminder awaiting approval". `cp-10-batch-approved.png`.
- Mailpit (`search?query=to:contact@acme.example.com`) → **2 messages**:
  - `cFQQFgHQxkTgVZYSXTRDcc` — "Friendly Reminder: Invoice INV-0001 – Payment Due"
  - `UirTRhJJnaYyMpEzg7M8Eu` — "Final Reminder: Outstanding Payment Required — INV-0002"
- Body (rendered HTML view): template-rendered facts table — **Invoice Number INV-0001, Amount Due 1725, Due Date 2026-07-01** — and payment CTA href `http://localhost:3002/invoices/ab76915f-...` (portal-URL fallback; no PSP configured on e2e — CTA href exists, no PSP domain asserted, per GAP-L-64 precedent). Live-AI subjects reference invoice numbers (shape-based).

## 15. CP-11 — Ledger shows SENT + audit

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-11.1 | A1/A2 invoice detail "Collection activity" | Status SENT (success badge) | [ ] |
| CP-11.2 | Audit `GET /api/audit-events?eventType=collections.reminder.sent` | Rows present for A1/A2 | [ ] |

**Evidence**: `cp-11-a1-sent-ledger.png`.

**Status: [x] PASS**

**Evidence log (CP-11):**
- INV-0001 detail "Collection activity" → **Stage 1 Sent**. `cp-11-a1-sent-ledger.png`. DB confirm: INV-0001 STAGE_1=SENT, INV-0002 STAGE_3=SENT (ESCALATION still FLAGGED, independent).
- Audit `GET /api/audit-events?eventType=collections.reminder.sent` → **2 rows**, actor "Alice Owner"; sample details `{stage:STAGE_3, invoice_number:INV-0002, delivery_log_id:da3e616a-...}`.

## 16. CP-12 — Payment cancels the pending gate  [deviation: manual route — see §3.1.2]

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-12.1 | Invoice B2 detail → "Record Payment" (SENT invoice) | Invoice → PAID | [ ] |
| CP-12.2 | B2 "Collection activity" | CANCELLED_PAYMENT (reason `invoice_paid`); gate EXPIRED | [ ] |
| CP-12.3 | Queue | B2 no longer listed | [ ] |
| CP-12.4 | Audit `GET /api/audit-events?eventType=collections.reminder.cancelled` | Row present | [ ] |
| CP-12.5 | Attempt to approve the now-EXPIRED B2 gate | HTTP 400 "Gate must be PENDING but was EXPIRED" → Failed disposition / error toast | [ ] |

**Evidence**: `cp-12-b2-cancelled-payment.png`, `cp-12-reapprove-refused.png`.

**Status: [x] PASS** — manual "Record Payment" deviation (see §3.1.2).

**Evidence log (CP-12):**
- INV-0004 (B2) detail → "Record Payment" (reference "QA-CP12-PAYMENT") → "Confirm Payment". Invoice → **PAID** (observed in UI header).
- AFTER_COMMIT listener fired: B2 "Collection activity" → **Stage 1 Cancelled Payment / Invoice Paid** (UI + DB: STAGE_1=CANCELLED_PAYMENT reason=invoice_paid). Gate → **EXPIRED** (DB). `cp-12-b2-cancelled-payment.png`.
- Queue no longer lists B2 → "No reminders". `cp-12-reapprove-refused.png`.
- Audit `collections.reminder.cancelled` → 1 row `{stage:STAGE_1, reason:invoice_paid, invoice_number:INV-0004, actor:System}`.
- **Re-approve refusal** (gate no longer in queue UI → tested via sanctioned gate API): single `POST /api/ai/gates/{id}/approve` → **HTTP 400** `{"detail":"Gate must be PENDING but was EXPIRED","title":"Invalid gate status"}`; batch `POST /api/ai/gates/batch-approve` → HTTP 200 with disposition `{outcome:"FAILED","error":"Invalid gate status: Gate must be PENDING but was EXPIRED"}`.

## 17. CP-13 — Cash digest (AI on)

**Actor**: system (JobWorker). **FLAGGED**: authorized `job_queue` INSERT with `job_type='cash_digest'`.

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-13.1 | Clear Mailpit | empty | [ ] |
| CP-13.2 | Trigger `cash_digest` via authorized INSERT (flagged) → poll job row up to ~90 s | job COMPLETED | [ ] |
| CP-13.3 | Mailpit | "Weekly cash digest" to `alice@e2e-test.local` AND `bob@e2e-test.local`, NOT `carol@e2e-test.local` | [ ] |
| CP-13.4 | Digest body | Deterministic tables (outstanding total, buckets Current/30d/60d/90d+, billed vs collected, stale WIP, activity counts, top risks) + AI narrative block (shape only) | [ ] |
| CP-13.5 | Bell (Alice + Bob) | "Your weekly cash digest is ready" | [ ] |
| CP-13.6 | Audit `GET /api/audit-events?eventType=collections.digest.sent` | details `{recipients, emails_sent, ai_narrated, outstanding_total}` | [ ] |

**Evidence**: Mailpit message IDs, `cp-13-digest-email.png`, `cp-13-digest-bell.png`, `cp-13-bob-digest-bell.png`.

**Status: [x] PASS**

**Evidence log (CP-13):**
- **Seed-state discrepancy (documented, not a product bug):** the seeded tenant contained only Alice (owner) + Carol (member) — **Bob (admin) was absent**. The first digest run therefore correctly sent to 1 recipient (Alice, the only owner/admin) and excluded Carol (member). To exercise the full owner+admin recipient set, **Bob was provisioned via the sanctioned internal members-sync path** (`POST /internal/members/sync`, orgRole=admin, exactly as `seed.sh` does — HTTP 201) and the digest re-run. Logged as **GAP-P83-004** (seed/environment).
- **[FLAGGED job trigger]** `INSERT ... 'cash_digest' ...` → job COMPLETED (~6 s). Mailpit cleared before each run.
- Digest emails (subject **"Weekly cash digest"**) → **`alice@e2e-test.local` AND `bob@e2e-test.local`**, NOT `carol@e2e-test.local`. IDs `Vr89aXG7qKqAfFAQpYNJ9P` (alice), `3GcC6cc3ohXVVoAQRemoki` (bob).
- Body: deterministic tables (Outstanding **6900.00**, Current/30/60/90 buckets, Billed vs Collected, stale WIP) **PLUS** an AI narrative block (shape-based — "Summary"/"Risk" sections + narrative sentences e.g. "significant shortfall in cash conversion this week"). `cp-13-digest-email.png`.
- Bell "Your weekly cash digest is ready" for **Alice** (`cp-13-digest-bell.png`) and **Bob** (`cp-13-bob-digest-bell.png`).
- Audit `collections.digest.sent` → `{recipients:2, emails_sent:2, ai_narrated:true, outstanding_total:6900.00}`.

## 18. CP-14 — AI off → numbers-only digest

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-14.1 | `/settings/integrations` → AI Assistant → toggle "Enabled" OFF (or Remove Key) | AI disabled | [ ] |
| CP-14.2 | Clear Mailpit; re-trigger `cash_digest` (FLAGGED INSERT; dedup clears once prior row COMPLETED) → poll | job COMPLETED | [ ] |
| CP-14.3 | Digest email | Tables present, NO narrative / AI-risks block | [ ] |
| CP-14.4 | Bell | Still fires "Your weekly cash digest is ready" | [ ] |

**Evidence**: `cp-14-numbers-only-digest.png` + message ID.

**Status: [x] PASS**

**Evidence log (CP-14):**
- Integrations → AI Assistant "Enabled" toggled OFF → badge **"Disabled"** (provider resolves to `noop` → narrative null).
- **[FLAGGED job trigger]** Mailpit cleared, re-triggered `cash_digest` → COMPLETED. Digest still delivered to **alice + bob** (IDs `2dhZPiutSEqgs7ABHfWaCC` / `oGTp2RLfonFYbRBTNv5Cvm`).
- Body: deterministic tables present (Outstanding 6900, Billed, Collected, Current) but **NO narrative / AI-risks block** — the "Summary"/"Risk"/narrative sentences are absent; body shrank from ~11 922 → ~8 363 chars vs the AI-on digest. `cp-14-numbers-only-digest.png`.
- Audit `collections.digest.sent` → `{recipients:2, emails_sent:2, ai_narrated:false, outstanding_total:6900.00}`.
- Bell still fires "Your weekly cash digest is ready" (Alice bell now shows 3 digest notifications across all runs).

## 19. CP-15 — Exempt customer produced nothing

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-15.1 | INV-C1 detail "Collection activity" | "No collection activity for this invoice yet." | [ ] |
| CP-15.2 | Gamma customer detail "Chase history" | "No chase history for this customer yet." | [ ] |
| CP-15.3 | Debtor book Gamma row | "Exempt" badge | [ ] |
| CP-15.4 | `GET /api/collections/activities?invoiceId={C1}` | `[]` | [ ] |
| CP-15.5 | Mailpit at any point | No email to `accounts@gamma.example.com` | [ ] |

**Evidence**: `cp-15-gamma-no-activity.png`.

**Status: [x] PASS**

**Evidence log (CP-15):**
- INV-0005 (Gamma, exempt) detail "Collection activity" → **"No collection activity for this invoice yet."** `cp-15-gamma-no-activity.png`.
- Gamma customer detail "Chase history" → "No chase history for this customer yet." (observed in CP-04). Debtor book Gamma row → **"Exempt"** badge (CP-07).
- `GET /api/collections/activities?invoiceId=2e6d10fb-...` → **`[]`**. DB: 0 `collection_activities` rows for INV-0005.
- Mailpit: **0 emails** to `accounts@gamma.example.com` across the entire run.

## 20. CP-16 — Regression gates + gap report

Regression gates (backend `./mvnw clean verify`; frontend `pnpm lint && build && test && format:check`) are
**owned by the orchestrator** (no product code changed in this epic — baseline confirmation). This agent does
NOT run them. The gap report is appended below.

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-16.1 | Backend `./mvnw clean verify` | Orchestrator-owned (not run by this agent) | [ ] |
| CP-16.2 | Frontend gates | Orchestrator-owned (not run by this agent) | [ ] |
| CP-16.3 | Gap report appended | See §22 | [ ] |

---

## 21. Execution Summary

Executed 2026-07-11 against the E2E mock-auth stack (frontend 3001 / backend 8081 / Mailpit 8026 / Postgres 5433), browser-driven via Playwright MCP, with a live Anthropic key (BYOAK) so the full AI ladder was exercised.

- **PASS: 16** — CP-00, CP-01, CP-02, CP-03, CP-04, CP-05, CP-06, CP-07, CP-08, CP-09, CP-10, CP-11, CP-12, CP-13, CP-14, CP-15.
- **FAIL: 0**
- **DEFERRED: 1** — CP-16 backend + frontend regression gates are **orchestrator-owned** (no product code changed this epic → baseline confirmation; not run by the QA agent). The CP-16 gap report itself is complete (below).
- **BLOCKED: 0**

Notes:
- All three authorized deviations were used and flagged: job_queue INSERT trigger (CP-06/09/13/14), manual "Record Payment" (CP-12), live BYOAK key via UI dialog (CP-03, never persisted anywhere).
- Every `job_queue` INSERT and every psql read is flagged `[FLAGGED job trigger]` / "flagged read-only" in its evidence log.
- Two seed-state gaps (missing Acme customer, missing Bob admin) were root-caused and repaired via sanctioned product-API / internal-sync paths, not raw SQL — filed as GAP-P83-004.
- Artefacts: 21 screenshots under `qa/testplan/artifacts/phase83/`; Mailpit message IDs, job-row statuses, and audit JSON excerpts inline above.

---

## 22. Gap Reporting Format

Gaps found during execution are recorded below using this template (per the phase-plan convention).
No product code is fixed and no scenario is amended in this epic (quality gate §6/§7); gaps are filed
for re-spec.

```
### GAP-P83-NNN: [Short title]

**Track**: [checkpoint id]
**Step**: [Step number]
**Category**: state-machine-error | missing-feature | ui-error | ...
**Severity**: blocker | major | minor | cosmetic
**Description**: [expected vs found]
**Evidence**: [endpoint / expected / actual / screenshot path]
**Suggested fix**: [If obvious]
```

### Gaps filed

The lifecycle itself passed end-to-end — none of these change a PASS verdict. They are not all environment-only, though: **GAP-P83-001 is a product gap** (no on-demand trigger endpoint for `collections_scan`/`cash_digest`) and **GAP-P83-005 is a possible UI defect** (firm-profile double-submit, unreproduced). GAP-P83-002/003/004 are e2e-stack/seed limitations. No product code was fixed and no scenario was amended (quality gates §6/§7).

### GAP-P83-001: No on-demand trigger endpoint for `collections_scan` / `cash_digest`

**Track**: CP-06, CP-13, CP-14
**Step**: CP-06.1
**Category**: missing-feature
**Severity**: major
**Description**: Phase 83 ships only cron fan-outs (collections daily 06:00, digest Monday 07:00). There is no HTTP trigger to run a scan or digest on demand — `JobQueueAdminController` only lists/retries/deletes; there is no analogue to `portal`'s `POST /internal/portal/digest/run-weekly`. QA therefore could not trigger the loop through any product surface.
**Evidence**: `collections/CollectionsScanScheduler.java:27-32`, `collections/CashDigestScheduler.java:27-32`, `infrastructure/jobqueue/JobQueueAdminController.java:16-49` (no enqueue); contrast `portal/notification/PortalDigestInternalController.java:32-40`. Execution used the authorized `INSERT INTO public.job_queue (...)` fallback (flagged in every use).
**Suggested fix**: add internal endpoints `POST /internal/collections/scan/run` and `POST /internal/collections/digest/run-weekly` (INTERNAL_API_KEY-gated, tenant fan-out) mirroring `PortalDigestInternalController`. Product work — out of scope for Epic 594.

### GAP-P83-002: e2e Docker image has no stub AI provider (stub-parity gap)

**Track**: CP-03, CP-07, CP-10, CP-13
**Step**: CP-03
**Category**: missing-feature (test-infra)
**Severity**: minor
**Description**: The Epic 594 task text assumed a "stub AI provider" on the e2e stack (port 3001). `StubAiProvider` + `ai/stubs/*/response.json` are test-JVM only (`testutil/TestAiConfiguration.java`); the e2e backend runs `SPRING_PROFILES_ACTIVE=e2e` with no AI env, so AI resolves to `noop`. Exercising drafting/gates/emails/narrative therefore requires a **live** Anthropic key (BYOAK) — which was provided and used for this run. Without a key, CP-07/09/10/11/12/13-narrative would be DEFERRED and only the deterministic remainder observable.
**Evidence**: `testutil/StubAiProvider.java:90-97`, `testutil/TestAiConfiguration.java:9-12`, `collections/AiReminderComposer.java:76-79`; e2e compose has zero AI env.
**Suggested fix**: bake a stub/`fake` AI provider into the e2e image (activate a deterministic composer under the `e2e` profile) so the AI ladder is exercisable without a live key and without per-call cost. Test-infra work — out of scope for Epic 594.

### GAP-P83-003: accounting-za profile — trust-balance customer & `TRUST_FUNDS_AVAILABLE` badge unobservable

**Track**: (optional trust step, dropped)
**Step**: n/a
**Category**: documented-profile-limitation
**Severity**: cosmetic
**Description**: The e2e tenant is `accounting-za`, which has no trust-accounting module, so the optional "one customer with a trust balance" step and the `TRUST_FUNDS_AVAILABLE` triage badge cannot be produced or observed. Not a defect — a profile limitation.
**Evidence**: `compose/docker-compose.e2e.yml:148`, `compose/seed/seed.sh:39` (vertical=accounting-za); badge `frontend/components/collections/triage-badges.tsx:11-17`.
**Suggested fix**: none required. If trust-badge coverage is wanted, run this capstone against a `legal-za` tenant. Documented, not filed for fix.

### GAP-P83-004: Freshly-seeded e2e tenant was missing the Acme customer and the Bob (admin) member

**Track**: CP-00, CP-04, CP-13
**Step**: CP-00.4
**Category**: seed/environment-error
**Severity**: minor
**Description**: The brief's CP-00 expected the seeded tenant to contain customer "Acme Corp" (ACTIVE) and members Alice/Bob/Carol. The freshly-seeded DB actually contained **zero customers** and only **Alice (owner) + Carol (member)** — **Acme Corp and Bob (admin) were absent** (`seed.sh` Steps 3 and 5 apparently did not take on this stack). Downstream impact: the invoice matrix needed Acme, and the digest owner+admin recipient set needed Bob. Both were created via the sanctioned product-API (`POST /api/customers` + lifecycle) and internal members-sync (`POST /internal/members/sync`, HTTP 201) paths — never raw SQL — and the affected checkpoints re-run cleanly.
**Evidence**: flagged read-only psql `SELECT ... FROM tenant_7d218705360b.customers` → 0 rows pre-seed; `... FROM members JOIN org_roles` → only owner+member. `seed.sh:82-89` (member sync), `:131-207` (Acme create+activate).
**Suggested fix**: investigate why the e2e boot-seed's customer + Bob-member steps did not persist on this reseed (idempotency/ordering, or a partial-wipe reseed that didn't re-run `seed.sh`). Environment/tooling — out of scope for Epic 594 product code.

### GAP-P83-005: Firm-profile "Complete Setup" produced two `ai_firm_profiles` rows (low-confidence, unreproduced)

**Track**: CP-03
**Step**: CP-03.5
**Category**: ui-error (possible double-submit)
**Severity**: minor
**Description**: After a single "Complete Setup" click on `/settings/ai`, the tenant's `ai_firm_profiles` table held **2** rows (earliest timestamp coincided with page load). The composer only checks `count() > 0`, so AI drafting worked correctly and the lifecycle was unaffected; but two profile rows from one setup flow suggests a missing submit-guard / an on-load autosave. **Not isolated to a definite mechanism this cycle** — logged as a low-confidence observation for investigation, not a confirmed defect, per reproduce-before-fix.
**Evidence**: flagged read-only psql `SELECT count(*), min(created_at) FROM tenant_7d218705360b.ai_firm_profiles` → `2`.
**Suggested fix**: if reproduced, disable the submit button while the mutation is in flight and/or make firm-profile creation upsert-by-tenant. Needs reproduction first — out of scope for Epic 594.
