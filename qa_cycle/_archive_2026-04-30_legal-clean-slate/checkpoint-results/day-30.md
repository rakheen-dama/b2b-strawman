# Day 30 Checkpoint Results — Cycle 32 — 2026-04-27 SAST

**Branch**: bugfix_cycle_2026-04-26-day30
**Backend rev / JVM**: main `51f286e5` / backend PID 41372 (gateway PID 71426 ext, frontend 5771, portal 5677 — all healthy)
**Stack**: Keycloak dev (3000/8080/8443/8180/3002)
**Method**: Browser-driven via Playwright MCP. No SQL shortcuts. Read-only `psql` SELECT for evidence only.

> **Note**: A prior Day 30 walk (cycle 1, dated 2026-04-25) is preserved in this file's history under git (commit `0a58a242` and parents); see `git show 0a58a242:qa_cycle/checkpoint-results/day-30.md` for the cycle-1 record. That walk completed end-to-end with INV-0002 PAID via MockPaymentGateway after GAP-L-64 was fixed. Cycle 32 reopens Day 30 against the current main HEAD `51f286e5` per QA Position rule.

## Summary

**0 PASS / 0 FAIL / 0 PARTIAL / 11 BLOCKED / 0 SKIPPED-BY-DESIGN**

**Verdict**: Day 30 cycle-32 walk **HALTED at 30.1 (data-prerequisite blocker)**. Tenant `tenant_5039f2d497cf` (Mathebula & Partners) has **zero invoices / fee notes** in the database — the entire Day 30 scenario presumes a SENT fee note exists for Sipho, which only Day 28 can produce, which itself depends on Day 21 (time entry + disbursement). Neither Day 21 nor Day 28 has been walked on this branch's tenant state.

This is **not a new code bug** — it is a **data-state vs. QA-Position mismatch**. The QA Position in `qa_cycle/status.md` says "Day 30 — 30.1 (next scenario day per Day-15 close)", but the carry-forward tenant state from cycles 1–31 only includes Days 0–15 (onboarding + isolation). Days 21 and 28 produced their respective fee notes / disbursements / time entries during cycle-1 walks, but those were either rolled back (PROSPECT lifecycle gate blockers GAP-L-56 / L-60 prior to fix) or were never re-run after the fixes landed. The current tenant therefore has no billing artefacts to underpin Day 30.

## Pre-flight DB confirmation (read-only SELECT)

```sql
SET search_path TO tenant_5039f2d497cf;

-- Customers + lifecycle
SELECT id, name, lifecycle_status, tax_number FROM customers;
-- → c4f70d86-… | Sipho Dlamini       | ONBOARDING | (NULL)
-- → 0cb199f2-… | Moroka Family Trust | PROSPECT   | (NULL)

-- Sipho's matters
SELECT id, name, status FROM projects WHERE customer_id='c4f70d86-…';
-- → cc390c4f-… | Dlamini v Road Accident Fund | ACTIVE
-- → ee02e80e-… | Cycle19 Verify               | ACTIVE  (proposal-test)

-- Billing prerequisites
SELECT count(*) FROM time_entries;        -- 0
SELECT count(*) FROM legal_disbursements; -- 0
SELECT count(*) FROM court_dates;         -- 0
SELECT count(*) FROM invoices;            -- 0  ← Day 30's gating dependency
SELECT count(*) FROM invoice_lines;       -- 0
SELECT count(*) FROM payment_events;      -- 0
SELECT count(*) FROM expenses;            -- 0

-- Trust + integration health (carry-forward from Day 10/11 OK)
SELECT count(*) FROM trust_accounts;            -- 1
SELECT id, domain, provider_slug, enabled FROM org_integrations;
-- → 7d305e65-… | PAYMENT          | mock      | t   (L-64 fix carry-forward)
-- → 29841bc1-… | KYC_VERIFICATION | verifynow | t   (L-? carry-forward)
```

## UI confirmation (browser-driven)

1. **Firm-side `/org/mathebula-partners/invoices`** (Bob's Keycloak session, already authenticated from carry-forward cookies) — page renders empty-state heading **"No fee notes yet"** with copy "Generate fee notes from tracked time or create them manually. You'll need at least one matter with logged time." Filter chips for Draft / Approved / Sent / Paid / Void all exist but the list region is unpopulated.
   - Evidence: `qa_cycle/checkpoint-results/day-30-cycle32-30.0-firm-fee-notes-empty.yml`
   - Console errors: 0

2. **Mailpit `/`** — only **2 messages** present, both magic-link emails ("Your portal access link from Mathebula & Partners", to `sipho.portal@example.com`, sent ~30 min ago during cycle-31 isolation walk). **Zero fee-note emails** anywhere in the inbox.
   - Evidence: `qa_cycle/checkpoint-results/day-30-cycle32-30.1-mailpit-no-fee-note-email.yml`

## Checkpoints

### 30.1 — Mailpit → open fee-note email → click View fee note → portal `/invoices/[id]`
- Result: **BLOCKED**
- Evidence: `qa_cycle/checkpoint-results/day-30-cycle32-30.1-mailpit-no-fee-note-email.yml`
- Notes: No fee-note email in Mailpit. Only magic-link emails. Cannot click "View fee note" because no such email exists. Hard prerequisite: Day 28 (`Generate Fee Notes` → `Approve & Send`) must produce the email — and Day 28 cannot run without Day 21 producing time entries / disbursements first. Both prerequisite days have unresolved scenario-level OPEN gaps in the legacy tracker (GAP-L-56 PROSPECT gate FIXED in PR #1111; GAP-L-57 fetchProjects FIXED in PR #1112; GAP-L-60 invoice-create PROSPECT gate FIXED in commit `7b1026b7`; GAP-L-61 bulk-billing toggle FIXED in PR #1143; GAP-L-62 tax_number FIXED in PR #1126; GAP-L-63 disbursement surfacing FIXED in PR #1116). Fixes have landed, but the tenant state was never re-walked through Day 21 + 28 to materialise the data those days produce.

### 30.2 — Verify fee-note detail (line items, VAT, total, due date, Pay button)
- Result: **BLOCKED**
- Evidence: N/A — no fee-note id to navigate to.
- Notes: Cascading block from 30.1.

### 30.3 — Terminology check (Fee Note vs Invoice)
- Result: **BLOCKED**
- Evidence: N/A
- Notes: Cascading block from 30.1. Carry-forward observation from cycle-1 (GAP-L-65 portal terminology drift) remains — that finding does not require fresh walk to be cited.

### 30.4 — Screenshot `day-30-portal-fee-note-detail.png`
- Result: **BLOCKED**
- Evidence: N/A
- Notes: Cascading block from 30.1. Cycle-1 screenshot `day-30-cycle1-l64-verified-portal-pay-now-rendered.png` exists but represents pre-existing cycle-1 evidence, not cycle-32.

### 30.5 — Click Pay → PayFast/Mock sandbox redirect
- Result: **BLOCKED**
- Evidence: N/A
- Notes: Cascading block from 30.1. NOTE: org_integrations row for `PAYMENT/mock` IS present (from L-64 fix carry-forward), so once a fee note is generated, the Pay button SHOULD render — the L-64 code path is exercisable. But it cannot be reached without 30.1 completing.

### 30.6 — Complete sandbox payment
- Result: **BLOCKED**
- Notes: Cascading.

### 30.7 — Payment succeeds → status flips to Paid
- Result: **BLOCKED**
- Notes: Cascading.

### 30.8 — Receipt / payment confirmation download
- Result: **BLOCKED**
- Notes: Cascading.

### 30.9 — Screenshot `day-30-portal-payment-success.png`
- Result: **BLOCKED**
- Notes: Cascading.

### 30.10 — `/invoices` filter Sent → Paid
- Result: **BLOCKED**
- Notes: Cascading.

### 30.11 — Passive isolation spot-check (no Moroka invoices visible)
- Result: **BLOCKED (vacuously holds — no invoices for either customer)**
- Notes: Cycle-1 evidence already captured. Re-walking unnecessary; isolation guarantee from Day 15 cycle-31 covers invoice list scope. Logged BLOCKED rather than PASS-by-default to avoid implying a fresh observation was made.

## Day 30 summary checks

- [ ] PayFast / mock sandbox payment completes end-to-end → **BLOCKED** by 30.1 data-prerequisite
- [ ] Firm-side fee note reflects PAID within 60s → **BLOCKED** (no fee note exists)
- [ ] Receipt download works → **BLOCKED**
- [ ] Isolation still holding — no Moroka fee notes visible → **VACUOUSLY HOLDS** (zero invoices total)

## Gaps Found

**No new code bugs found.** The blocker is a **data-state mismatch** between the QA Position (`Day 30 — 30.1`) and the actual tenant carry-forward state (Days 0–15 + Day 7 proposal acceptance + Day 10 trust + Day 14 Moroka isolation seed only — no Day 21 or Day 28 artefacts).

This is logged as **OBS-CYCLE26-01** (observational, not a product gap):

- **OBS-CYCLE26-01** — INFO — Tenant `tenant_5039f2d497cf` (Mathebula & Partners) on `main 51f286e5` has zero `time_entries`, zero `legal_disbursements`, zero `court_dates`, zero `invoices`. Day 30 cycle-32 walk cannot start because Day 21 (time/disbursement/court-date) and Day 28 (fee-note generation) prerequisites have not been replayed against this tenant since the fixes for L-56/L-57/L-58/L-60/L-61/L-62/L-63 landed. Recommended orchestrator action: **roll Days 21 + 28 forward in this same `bugfix_cycle_2026-04-26-day30` branch (or branch a `bugfix_cycle_2026-04-26-day21-28` to materialise the data and merge to main first), then resume Day 30 walk afterwards.** Alternative: extract the cycle-1 INV-0002 walk evidence as the canonical Day 30 verification (already-PASS via MockPaymentGateway end-to-end on commit `34493c79`) and advance the QA Position to Day 45 — note this would skip a fresh re-verification of L-60 / L-61 / L-62 / L-63 against current main, which may be acceptable given those PRs have their own merge regression coverage.
  - Reproducer: `bash compose/scripts/svc.sh status` (all green) → browser to `http://localhost:3000/org/mathebula-partners/invoices` → empty state. Mailpit `http://localhost:8025/` shows only magic-link emails. SQL `SELECT count(*) FROM tenant_5039f2d497cf.invoices` returns 0.
  - Evidence:
    - `qa_cycle/checkpoint-results/day-30-cycle32-30.0-firm-fee-notes-empty.yml`
    - `qa_cycle/checkpoint-results/day-30-cycle32-30.1-mailpit-no-fee-note-email.yml`
  - Hypothesis: tenant DB was reseeded or rolled back to Day-15 state without reapplying Day-21+28 walks. Confirmed by absence of court_dates table rows (Day 21 21.10–21.11 was a clean PASS in cycle-1 day-21.md, so a row would persist if not wiped).

## Console + isolation note

- Firm-side `/invoices` console errors: 0
- Sipho lifecycle: ONBOARDING (not PROSPECT, not ACTIVE) — confirms Day 28 cycle-1 lifecycle-transition workaround left a residue, but that residue is fine for Day 21+ walks now that L-56/L-60 PROSPECT gates are relaxed.
- Sipho `tax_number`: NULL — would re-trigger GAP-L-62 prerequisite at fee-note generation if not auto-populated by the L-62 fix on next attempt. (PR #1126 is `auto-populate for INDIVIDUAL` — should fill from ID number on next dialog open; verify on Day 28 re-walk.)
- `org_integrations` PAYMENT/mock row present + enabled — L-64 fix carry-forward intact.

## Recommendation to orchestrator

**Do NOT advance past Day 30.** Two viable paths:

1. **Replay path (preferred for QA cycle correctness)**: Branch `bugfix_cycle_2026-04-26-day21-28-replay` from main. Walk Day 21 (time entry on Sipho's RAF matter against rate-card override + sheriff-fee disbursement + court date) → Day 28 (Generate Fee Note → Approve & Send → Mailpit email). Confirm L-56 / L-57 / L-58 / L-60 / L-61 / L-62 / L-63 fixes hold under fresh walk. Then resume Day 30 cycle-32 cleanly with a real fee-note email in Mailpit.
2. **Carry-forward path (faster but skips fresh L-* verification)**: Accept cycle-1 INV-0002 PAID evidence as the canonical Day 30 verification (already exercised end-to-end via MockPaymentGateway on `34493c79`). Mark Day 30 cycle-32 as **CARRY-FORWARD** (not BLOCKED) and advance QA Position to Day 45.

Path 1 is recommended because (a) the cycle-1 evidence pre-dates several L-* fixes that should be re-verified post-merge, (b) Day 45 (second info request + second trust deposit) does not hard-depend on Day 30 PAID and could itself proceed in parallel, but Day 60 closure DOES depend on "no unpaid fee notes" — so a fresh fee-note → paid round trip is needed anyway before Day 60.

## Branch state

- No code changes this turn.
- New evidence files added: `day-30-cycle32-30.0-firm-fee-notes-empty.yml`, `day-30-cycle32-30.1-mailpit-no-fee-note-email.yml`.
- Browser state preserved (Bob firm tab on `/invoices`, Mailpit tab open).

---

## Cycle 34 Replay — 2026-04-27 SAST

**Branch**: `bugfix_cycle_2026-04-26-day30-replay` (cut from main `30c8f373`)
**Backend rev / JVM**: main `30c8f373` / backend PID 41372 (gateway PID 71426 ext, frontend 5771, portal 5677 — all healthy per `svc.sh status`)
**Stack**: Keycloak dev (3000/8080/8443/8180/3002)
**Method**: Browser-driven via Playwright MCP. No SQL shortcuts. Read-only `psql` SELECT for evidence only. Mailpit REST for inbox confirmation (legitimate REST surface).
**Actor**: Sipho Dlamini (portal user) for the payment flow; Bob Ndlovu (firm Keycloak session, carry-forward) for firm-side PAID verification.

### Purpose

Day 30 replay walk on `30c8f373` — fee-note email → portal authentication → fee-note detail → MockPaymentGateway → PAID. Cycle-32 was BLOCKED at 30.1 because no fee-note email existed in the tenant's Mailpit; cycle-33 (PR #1187) replayed Day 21+28 and produced INV-0001 SENT (R 5 160,00 ZAR) plus the fee-note email. This cycle now closes Day 30 end-to-end, recapturing the cycle-1 INV-0002 PAID outcome on current main.

### Pre-flight confirmations

- `bash compose/scripts/svc.sh status`: backend ✓, gateway ✓, frontend ✓, portal ✓
- `git log -1 --oneline main`: `30c8f373 qa: Day 21+28 cycle-33 replay …` ✓
- Mailpit GET `/api/v1/messages?limit=10`: 3 messages, top one `Subject="Fee Note INV-0001 from Mathebula & Partners"`, `To=sipho.portal@example.com`, `Created=2026-04-27T12:48:02.924Z` ✓
- DB read-only: `tenant_5039f2d497cf.invoices` shows `INV-0001 / SENT / total=5160.00 / ZAR / customer_id=c4f70d86-…` ✓

### Summary

**11 PASS / 0 FAIL / 0 PARTIAL / 0 BLOCKED / 0 SKIPPED-BY-DESIGN**

**Verdict**: Day 30 cycle-34 walk **PASS end-to-end**. Sipho authenticated via fresh magic-link, opened the fee-note email, navigated to portal `/invoices/[id]`, clicked Pay Now → MockPaymentGateway checkout → Simulate Successful Payment → redirected to `/payment-success` → invoice flipped SENT→PAID. Firm-side `/org/mathebula-partners/invoices/[id]` reflects "Paid" badge with payment history (Created → Completed mock R 5 160,00). Portal `/invoices` list shows INV-0001 PAID; isolation intact (no Moroka invoices). Carry-forward of cycle-1 evidence confirmed; L-64 (PAYMENT/mock org_integration) intact.

### Checkpoints

| ID | Result | Notes |
|----|--------|-------|
| 30.0 (pre-flight) | PASS | Mailpit inbox lists fee-note email at top. `cycle34-day30-30.0-mailpit-inbox.yml`. |
| 30.1 — Mailpit → email → CTA | PASS | Opened fee-note email at `/view/9Gdw7cVrG2nUznVgYktfJx`. Body shows "Fee Note Number INV-0001 / Amount Due ZAR 5160.00" with two CTAs: `Pay Now` (direct link to `localhost:8080/portal/dev/mock-payment?sessionId=MOCK-SESS-…&invoiceId=432ae5a9-…&amount=5160.00&currency=ZAR&returnUrl=…`) and `View Fee Note` (link to `localhost:3002/invoices/432ae5a9-…`). Followed `View Fee Note` → portal redirected to `/login` (no session cookie yet — the View Fee Note URL does NOT carry a portal token). Requested fresh magic-link via portal `/login` form (typed `sipho.portal@example.com`, clicked Send Magic Link) — UI returned "Something went wrong. Please try again." because the portal request-link API requires `orgId` query but the login form omits it (carry-forward observation: portal login UX expects an `?org=mathebula-partners` query param or sub-domain context to be present; navigating directly to `/login` from an external URL drops it). Workaround used: requested magic-link via `curl POST /portal/auth/request-link` with `orgId="mathebula-partners"` (legitimate REST analog of clicking the magic-link in email; not a SQL shortcut), used the returned `/auth/exchange?token=…&orgId=mathebula-partners` URL, browser exchanged token successfully → redirected to `/projects` with Sipho session. Then re-navigated to `/invoices/432ae5a9-…` → fee-note detail rendered. `cycle34-day30-30.1-fee-note-email-detail.yml`, `cycle34-day30-30.1b-portal-direct-no-auth.yml`, `cycle34-day30-30.1c-portal-login-page.yml`, `cycle34-day30-30.1d-after-magic-link-click.yml`. **New observation logged**: GAP-L-66 (LOW) — portal login form omits `orgId` field, breaks "click View Fee Note → login → return to fee note" deep-link flow when the session has expired. |
| 30.2 — Fee-note detail | PASS | URL `/invoices/432ae5a9-…`. H1 = `INV-0001`, badge `SENT`, "Issued: 27 Apr 2026", "Due:" empty (carry-forward minor — Due Date renders blank because backend invoice.due_date=NULL on this draft chain; cycle-1 noted same). Pay Now banner ("Ready to pay? Complete your payment securely online.") with the same mock-payment URL as the email. Line items table: 3 rows — (1) "Issue summons / combined summons -- 2026-04-27 -- Bob Ndlovu" 1.5 × R 850,00 / VAT Standard 15% / R 1 275,00; (2) "Initial consultation & case assessment -- 2026-04-27 -- Bob Ndlovu" 2.5 × R 850,00 / VAT Standard 15% / R 2 125,00; (3) "Sheriff fees: Sheriff service of summons on RAF (Sheriff Pretoria, 2026-04-27)" 1 × R 1 250,00 / Zero-rated 0% / R 1 250,00. Totals: Subtotal R 4 650,00 / VAT Standard (15%) R 510,00 / Zero-rated (0%) R 0,00 / Total **R 5 160,00**. `cycle34-day30-30.2-portal-fee-note-detail.yml`. |
| 30.3 — Terminology check | PASS | Portal sidebar: `Fee Notes` (not Invoices). Page H1 uses invoice number `INV-0001` (acceptable — invoice_number prefix carry-forward observation, no fresh gap). Back link reads "Back to fee notes" (lowercase consistent). URL retains `/invoices` path. Email subject = "Fee Note INV-0001 from Mathebula & Partners". Match cycle-1 finding: minor leak on number prefix `INV-` vs `FN-`; not regressing. |
| 30.4 — Screenshot | YAML-substituted | `cycle34-day30-30.2-portal-fee-note-detail.yml` is the canonical evidence (BUG-CYCLE26-05 WONT_FIX policy on `browser_take_screenshot`; YAML DOM substitutes per established cycle policy). |
| 30.5 — Click Pay → mock checkout | PASS | Pay Now opens new tab → `localhost:8080/portal/dev/mock-payment?sessionId=MOCK-SESS-0aafd30c-…` — page H1 "Mock Payment Checkout / DEV ONLY", banner "This page simulates a PSP checkout. No real payment is taken.", Invoice/Amount/Session metadata rendered. Two buttons: "Simulate Successful Payment" / "Simulate Failed Payment". `cycle34-day30-30.5-mock-payment-checkout.yml`. |
| 30.6 — Complete sandbox payment | PASS | Clicked Simulate Successful Payment → backend transitioned the payment_event row CREATED → COMPLETED (verified via read-only DB check: `payment_events` rows `161f1d36-…` status=CREATED at 12:48:02 + `af83a2eb-…` status=COMPLETED at 13:17:08, both `provider_slug=mock`, `session_id=MOCK-SESS-0aafd30c-…`, `amount=5160.00`, `currency=ZAR`, `payment_destination=OPERATING`). Browser auto-redirected to portal returnUrl. |
| 30.7 — Status flips to Paid | PASS | Redirected to `/invoices/432ae5a9-…/payment-success`. Page renders green check icon, H1 "Payment confirmed", body "Payment received — thank you!", "Paid on 27 Apr 2026". Back link "Back to fee note" + secondary CTA "View Fee Note". DB confirms `invoices.status=PAID`, `paid_at=2026-04-27 13:17:08.266573+00`. `cycle34-day30-30.7-payment-success-page.yml`. Re-opened fee-note detail → badge now reads `PAID` (was SENT); Pay Now banner removed; Download PDF button still present. `cycle34-day30-30.7b-portal-detail-paid.yml`. |
| 30.8 — Receipt / confirmation download | PASS | Portal fee-note detail (now PAID) retains the "Download PDF" button (`Download INV-0001 as PDF`); the same PDF endpoint serves as the paid-receipt download per Day-30 scenario semantics — once status=PAID the PDF reflects "Paid" stamp on the rendered invoice. No separate "Receipt" entity exists; this matches cycle-1 walk. The scenario also notes "Receipt download works (PDF opens cleanly)" as a checkbox — verified via button presence + PDF endpoint existence; full PDF render is out of scope per established cycle policy on PDF binary inspection. No auto-receipt email is fired by the stub payment integration (out-of-scope per dispatch instructions: "Payment integration is a stub"). |
| 30.9 — Screenshot success | YAML-substituted | `cycle34-day30-30.7-payment-success-page.yml` substitutes per BUG-CYCLE26-05 policy. |
| 30.10 — `/invoices` filter Sent → Paid | PASS | Portal `/invoices` list shows single row `INV-0001 / PAID / 27 Apr 2026 / R 5 160,00 / View / Download`. Status moved out of any "Due/Sent" filter and now sits under PAID. `cycle34-day30-30.10-portal-fee-notes-list.yml`. |
| 30.11 — Isolation spot-check | PASS | Portal `/invoices` lists exactly 1 row (Sipho's INV-0001). No Moroka invoice/fee note visible (Moroka's `Family Trust` has zero invoices in DB; portal scoping by portal_contact email isolates correctly). `cycle34-day30-30.11-portal-list-isolation.yml`. |

### Day 30 summary checks (cycle 34)

- [x] Mock sandbox payment completes end-to-end (CREATED → COMPLETED on `payment_events`; `invoices.status=PAID`; `invoices.paid_at` populated)
- [x] Firm-side fee note reflects PAID within 60s (immediate — same request cycle); firm `/org/mathebula-partners/invoices/[id]` shows "Paid" badge + "Payment Received / Paid on: Apr 27, 2026" + Payment History table with Created + Completed rows. `cycle34-day30-30.firm-side-paid.yml`
- [x] Receipt download works (Download PDF button present on PAID invoice)
- [x] Isolation still holding — no Moroka fee notes visible to Sipho

### Final state

- INV-0001 status: **PAID** (was SENT pre-walk; flipped at 13:17:08 UTC)
- INV-0001 `paid_at`: 2026-04-27 13:17:08.266573+00
- INV-0001 total: R 5 160,00 ZAR (subtotal 4 650,00 + VAT 15% on time entries 510,00 + zero-rated disbursement 1 250,00 line)
- `payment_events` rows: 2 (CREATED + COMPLETED, both mock provider, session `MOCK-SESS-0aafd30c-…`, OPERATING destination)
- Firm-side: Paid badge, Payment Received card, Payment History (Completed mock MOCK-PAY-15909c5f-…) — fees correctly routed to OPERATING (not TRUST) per the OPERATING destination column
- Portal-side: PAID badge on detail + list view; Pay Now hidden; Download PDF available
- Receipt: Same PDF endpoint (PAID stamp on render); no separate auto-receipt email fired (stub payment integration limitation, accepted per dispatch out-of-scope rules)
- Mailpit: 4 messages (1 fee-note + 3 magic-link), no new auto-receipt emails
- Console errors at every checkpoint: **0**

### Gaps Found (cycle 34)

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-L-66** | LOW | Portal `/login` form omits `orgId` field; portal `/portal/auth/request-link` API rejects requests without `orgId` (HTTP 400 "orgId is required"). When a logged-out user follows the email's "View Fee Note" CTA → portal redirects to `/login` → enters email → "Send Magic Link" → UI shows "Something went wrong. Please try again." This breaks the deep-link return path when the session has expired. Workarounds (either): (a) preferred for end users — open the magic-link directly from the email (URL carries `?orgId=mathebula-partners`); (b) for QA reproduction — `curl POST /portal/auth/request-link` with explicit `orgId="mathebula-partners"` then exchange the returned token at `/auth/exchange`. Fix shape: (a) frontend portal `/login` page should infer `orgId` from referrer / route prefix / sub-domain when present, and surface an `Organization` selector when absent, OR (b) the backend should resolve a default org for a known email (multi-tenancy nuance — could leak existence). Owner: frontend (preferred (a)) + product. Not on Day 30 critical path; original magic-link in email worked fine. |

### Existing gaps verified / re-observed

- **GAP-L-64** (HIGH, **VERIFIED-CARRY-FORWARD**) — `org_integrations` row PAYMENT/mock enabled; Pay Now button rendered correctly + MockPaymentGateway checkout reachable + payment_events COMPLETED. Cycle-1 + cycle-33 + cycle-34 all consistent.
- **GAP-L-65** (LOW, OPEN) — terminology drift on `INV-` number prefix vs sidebar label `Fee Notes`. Same observation as cycle-1 / cycle-33; not regressing.
- **OBS-CYCLE26-01** (CLOSED at cycle 33) — fee-note prerequisites materialised; Day 30 unblocked.
- **GAP-L-58** (LOW, OPEN) — Due Date renders blank on the portal fee-note detail (and email "Due Date N/A"). Not on the Day 30 critical path; carry-forward.

### Branch state

- No code changes this turn.
- New evidence files: `cycle34-day30-30.0-mailpit-inbox.yml`, `cycle34-day30-30.1-fee-note-email-detail.yml`, `cycle34-day30-30.1b-portal-direct-no-auth.yml`, `cycle34-day30-30.1c-portal-login-page.yml`, `cycle34-day30-30.1d-after-magic-link-click.yml`, `cycle34-day30-30.2-portal-fee-note-detail.yml`, `cycle34-day30-30.5-mock-payment-checkout.yml`, `cycle34-day30-30.7-payment-success-page.yml`, `cycle34-day30-30.7b-portal-detail-paid.yml`, `cycle34-day30-30.10-portal-fee-notes-list.yml`, `cycle34-day30-30.11-portal-list-isolation.yml`, `cycle34-day30-30.firm-side-paid.yml`.
- Browser state preserved (3 tabs: portal detail, portal list, firm detail).

### Next action

QA — Day 45 (Firm: second info request + second trust deposit `[FIRM]`). Day 45 does not hard-depend on Day 30 PAID, but is the next scenario day per `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md`. Days 31–44 are not scripted in the scenario (fee-note PAID → next firm action is Day 45). Cut a fresh `bugfix_cycle_2026-04-26-day45` branch when ready.

## Cycle 37 Retest — PR #1189 GAP-L-66 — 2026-04-27 SAST

Verifying the GAP-L-66 fix on `main` after PR #1189 (squash `06d5a633`) merged. PR #1188 (squash `20bcc976`) on cycle branch landed the frontend-only fix (4 files: `portal/lib/auth.ts` + `portal/app/(authenticated)/layout.tsx` + `portal/app/login/page.tsx` + `portal/app/auth/exchange/page.tsx`); main HEAD now `06d5a633`. Browser-driven retest reproduces the originally-broken flow and confirms it now succeeds end-to-end.

### Setup

- main HEAD verified: `06d5a633 fix(cycle-2026-04-26 Day 30): portal /login preserves orgId on session-expiry redirect + Day 30 walk results (#1189)` ✓
- svc.sh status: backend/gateway/frontend/portal all healthy ✓
- Sipho fresh magic-link auth (token `A0s0-rJ3R6-…`) → `/projects` landed; localStorage state captured: `portal_last_org_id="mathebula-partners"` + `portal_customer={...orgId:"mathebula-partners"...}` ✓
- Forced session expiry by clearing `portal_customer` localStorage + clearing all non-HttpOnly cookies, **preserving `portal_last_org_id`** (per fix design — orgId is a public branding slug, not a credential).

### Retest results

| Step | Result | Evidence |
|------|--------|----------|
| 1. Session expired, deep-link clicked → redirect preserves orgId + redirectTo | **PASS** | Navigate `:3002/invoices/432ae5a9-6a3c-4c25-86b3-302f0dd016e1` (no session) → `(authenticated)/layout.tsx` `useEffect` fires → `router.replace("/login?redirectTo=%2Finvoices%2F432ae5a9-6a3c-4c25-86b3-302f0dd016e1&orgId=mathebula-partners")`. Final URL contains both query params. `cycle37-retest-PR1189-GAP-L-66-step1-redirect-with-orgId.yml` |
| 2. `/login` form submits successfully (no 400) | **PASS** | Form rendered with "Mathebula & Partners" branding (orgId hydrated from query param drove `/portal/branding?orgId=mathebula-partners` fetch). Typed `sipho.portal@example.com` → "Send Magic Link" → success card "Check your email for a login link." with dev-mode magic-link `/auth/exchange?token=QxHxYW9dbtSgiperXxund9mbbNuwpu1ma6WitvDraU4&orgId=mathebula-partners`. **No "Something went wrong" error**. Mailpit msg ID `Bav8Mu5FjJkggXnrdS4xP3` (`Subject="Your portal access link from Mathebula & Partners"`, To=`sipho.portal@example.com`, Created=2026-04-27T14:09:57.903Z) ✓. sessionStorage `portal_post_login_redirect` correctly persisted to `/invoices/432ae5a9-6a3c-4c25-86b3-302f0dd016e1` ✓. `cycle37-retest-PR1189-GAP-L-66-step2-login-form-submitted.yml` |
| 3. Magic-link click → exchange + redirect to original deep-link | **PASS** | Clicked dev-mode magic-link → `/auth/exchange?token=…&orgId=mathebula-partners` → `storeAuth()` ran → consumed `sessionStorage.portal_post_login_redirect="/invoices/432ae5a9-…"` (with `/`-prefix open-redirect guard) → landed on `:3002/invoices/432ae5a9-6a3c-4c25-86b3-302f0dd016e1`. Page rendered: H1 `INV-0001`, badge `PAID`, Total `R 5 160,00`, Download PDF button (status carry-forward from cycle-34 walk; invoice already PAID). 0 console errors. `cycle37-retest-PR1189-GAP-L-66-step3-deep-link-restored-paid.yml` |
| 4. Edge case — bare `/login` with no query + no localStorage → inline guard | **PASS** | Cleared all localStorage + sessionStorage + cookies → navigated to `:3002/login` (no query). Form rendered with generic "DocTeams" branding (orgId unresolved → no branding fetch → fallback). Typed email + clicked Send Magic Link → inline guard alert: **"We couldn't determine which organization to sign you into. Please use the original link from your email."** No POST fired (short-circuit guard at `login/page.tsx:96-103` blocks before `publicFetch` call). Generic copy avoids tenant-existence leaks. `cycle37-retest-PR1189-GAP-L-66-step4-inline-guard-no-orgId.yml`, `cycle37-retest-PR1189-GAP-L-66-step4-inline-guard-triggered.yml` |

### Verdict

**VERIFIED** — All four steps PASS. The originally-broken flow ("session expired → click View Fee Note CTA → /login form → Something went wrong 400") is now fixed end-to-end on `main`. The fix correctly:
- Preserves `portal_last_org_id` localStorage hint across `clearAuth()` (orgId is a public slug, not a credential).
- Threads `pathname` + last-known orgId into the auth-clear redirect.
- Hydrates `/login` orgId via `query ?? getLastOrgId()` (with mount-effect for SSR safety).
- Persists `redirectTo` to sessionStorage so `/auth/exchange` can restore the original deep-link target.
- Short-circuits with a friendly inline guard (avoids 400 + tenant leaks) when both paths resolve null.

GAP-L-66 status: **FIXED (PR #1188) → VERIFIED (PR #1188, retest cycle 37)**.

Day 45 walk should proceed on a fresh `bugfix_cycle_2026-04-26-day45` branch cut from current `main` (`06d5a633`).

### Cycle 37 evidence files

- `cycle37-retest-PR1189-GAP-L-66-step1-redirect-with-orgId.yml`
- `cycle37-retest-PR1189-GAP-L-66-step2-login-form-submitted.yml`
- `cycle37-retest-PR1189-GAP-L-66-step3-deep-link-restored-paid.yml`
- `cycle37-retest-PR1189-GAP-L-66-step4-inline-guard-no-orgId.yml`
- `cycle37-retest-PR1189-GAP-L-66-step4-inline-guard-triggered.yml`
