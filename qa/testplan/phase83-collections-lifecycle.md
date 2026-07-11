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

**Evidence log (CP-00):**

## 6. CP-01 — Mock login

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-01.1 | Navigate `http://localhost:3001/mock-login` | Mock-login page loads | [ ] |
| CP-01.2 | Click "Sign In" (defaults to Alice, owner) | Redirected to dashboard | [ ] |

**Evidence**: `cp-01-dashboard.png`.

**Evidence log (CP-01):**

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

**Evidence log (CP-02):**

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

**Evidence log (CP-03):**

## 8. CP-04 — Customers: create 2, exempt 1

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-04.1 | Create "Beta Ltd" (`billing@beta.example.com`) via Customers UI → transition to ACTIVE | Beta Ltd ACTIVE | [ ] |
| CP-04.2 | Create "Gamma Corp" (`accounts@gamma.example.com`) via Customers UI → transition to ACTIVE | Gamma Corp ACTIVE | [ ] |
| CP-04.3 | Gamma detail (Details tab) → Collections card → toggle "Exclude from collections" ON | Helper "Exempt customers never receive automated payment reminders."; toggle on | [ ] |

**Evidence**: `cp-04-gamma-exempt.png`.

**Evidence log (CP-04):**

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

**Evidence log (CP-05):**

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
| CP-06.3 | Backend log excerpt: `docker compose -f compose/docker-compose.e2e.yml logs backend | grep -i collections` | scan ran | [ ] |

**Evidence**: job row COMPLETED + backend log excerpt.

**Evidence log (CP-06):**

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

**Evidence**: `cp-07-queue-4-drafts.png`, `cp-07-inv-a2-ledger.png`, `cp-07-debtor-book.png`.

**Evidence log (CP-07):**

## 12. CP-08 — Escalation: bell + audit

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-08.1 | Alice → bell (`aria-label="Notifications"`) | Text "Invoice {A2-number} is 70 days overdue — flagged for a partner call" | [ ] |
| CP-08.2 | Log in as Carol (member) → bell | Does NOT contain the escalation notification | [ ] |
| CP-08.3 | Audit `GET /api/audit-events?eventType=collections.escalation.flagged` | One row, entityType `collection_activity`, details `{invoice_id, invoice_number, days_overdue}` | [ ] |

**Evidence**: `cp-08-escalation-bell.png` + audit JSON excerpt.

**Evidence log (CP-08):**

## 13. CP-09 — Reject one draft (terminal per stage)

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-09.1 | B1 queue card → expand → "Reject" → "Reject Gate Action" dialog → confirm | Gate rejected | [ ] |
| CP-09.2 | B1 invoice detail "Collection activity" | STAGE_2 row status REJECTED | [ ] |
| CP-09.3 | Re-trigger scan (CP-06 SQL again — FLAGGED) | B1 STAGE_2 stays REJECTED; no new gate; B1 queue count unchanged | [ ] |

**Evidence**: `cp-09-b1-rejected.png`.

**Evidence log (CP-09):**

## 14. CP-10 — Batch approve the rest → Mailpit

**Actor**: Alice (owner).

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-10.1 | Clear Mailpit: `curl -X DELETE http://localhost:8026/api/v1/messages` | Mailpit empty | [ ] |
| CP-10.2 | Select A1 + A2 (leave B2 unselected) → "Approve selected (2)" | "Last batch result": 2 × "Approved" | [ ] |
| CP-10.3 | Mailpit: 2 messages to `contact@acme.example.com` | Subject references invoice number (shape); body has facts table (invoice number, amount, due date) + payment CTA href | [ ] |
| CP-10.4 | `curl "http://localhost:8026/api/v1/search?query=to%3Acontact%40acme.example.com"` → record IDs; `GET /api/v1/message/{id}` | Message IDs recorded; CTA href present (portal-URL fallback — no PSP domain asserted) | [ ] |

**Evidence**: Mailpit message IDs + `cp-10-reminder-email.png`.

**Evidence log (CP-10):**

## 15. CP-11 — Ledger shows SENT + audit

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-11.1 | A1/A2 invoice detail "Collection activity" | Status SENT (success badge) | [ ] |
| CP-11.2 | Audit `GET /api/audit-events?eventType=collections.reminder.sent` | Rows present for A1/A2 | [ ] |

**Evidence**: `cp-11-a1-sent-ledger.png`.

**Evidence log (CP-11):**

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

**Evidence log (CP-12):**

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

**Evidence**: Mailpit message IDs, `cp-13-digest-email.png`, `cp-13-digest-bell.png`.

**Evidence log (CP-13):**

## 18. CP-14 — AI off → numbers-only digest

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-14.1 | `/settings/integrations` → AI Assistant → toggle "Enabled" OFF (or Remove Key) | AI disabled | [ ] |
| CP-14.2 | Clear Mailpit; re-trigger `cash_digest` (FLAGGED INSERT; dedup clears once prior row COMPLETED) → poll | job COMPLETED | [ ] |
| CP-14.3 | Digest email | Tables present, NO narrative / AI-risks block | [ ] |
| CP-14.4 | Bell | Still fires "Your weekly cash digest is ready" | [ ] |

**Evidence**: `cp-14-numbers-only-digest.png` + message ID.

**Evidence log (CP-14):**

## 19. CP-15 — Exempt customer produced nothing

| # | Action | Expected | Status |
|---|--------|----------|--------|
| CP-15.1 | INV-C1 detail "Collection activity" | "No collection activity for this invoice yet." | [ ] |
| CP-15.2 | Gamma customer detail "Chase history" | "No chase history for this customer yet." | [ ] |
| CP-15.3 | Debtor book Gamma row | "Exempt" badge | [ ] |
| CP-15.4 | `GET /api/collections/activities?invoiceId={C1}` | `[]` | [ ] |
| CP-15.5 | Mailpit at any point | No email to `accounts@gamma.example.com` | [ ] |

**Evidence**: `cp-15-gamma-no-activity.png`.

**Evidence log (CP-15):**

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

_(populated after execution)_

- PASS:
- FAIL:
- DEFERRED:
- BLOCKED:

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

_(populated during execution)_
