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
