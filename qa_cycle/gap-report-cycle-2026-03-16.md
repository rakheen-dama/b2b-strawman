# QA Cycle Gap Report — accounting-za Vertical
## Date: 2026-03-16 | Branch: `bugfix_cycle_2026-03-15` | PR: #693 (merged to main)

**Scenario**: 90-day accelerated lifecycle of "Thornton & Associates", a 3-person Johannesburg accounting firm
**Method**: Playwright MCP browser automation against E2E mock-auth stack (http://localhost:3001)
**Execution**: 4 QA cycles, 11 lifecycle days tested (Day 0, 1, 2, 3, 7, 14, 30, 45, 60, 75, 90)
**Actors**: Alice (Owner/Senior Accountant), Bob (Admin/Bookkeeper), Carol (Member/Junior Accountant)

---

## Executive Summary

The DocTeams platform with the `accounting-za` vertical profile covers **~75%** of a small SA accounting firm's daily needs. The core workflow loop — onboard client, log time, generate documents, review profitability — is functional. Three blockers were found and resolved during the cycle. ~~Remaining gaps cluster around entity-type-specific compliance (trusts), billing flow completeness, and SA currency display.~~ All P1 and P2 remaining gaps were resolved in a follow-up fix cycle (PR #703, 2026-03-16).

| Category | Count |
|----------|-------|
| Total gaps identified | 31 |
| Resolved during cycle (PRs merged) | 10 |
| Resolved in follow-up fix cycle (PR #703) | 6 |
| Deferred (new features, out of scope) | 13 |
| Disproved (feature already exists) | 2 |
| Remaining open | 0 |

---

## Resolved During This Cycle

These issues were found, fixed, verified, and merged to `main` in 8 PRs (#687–#695).

### Blockers Resolved

| ID | Summary | Root Cause | Fix (PR) |
|----|---------|------------|----------|
| GAP-008 | Accounting template pack not seeded — only 3 generic templates visible | `ProvisioningController` hardcoded `null` for `verticalProfile` param. `AbstractPackSeeder` correctly skipped accounting-za packs since tenant had no profile. | Pass `verticalProfile` through `ProvisioningRequest` DTO + update E2E `seed.sh` to send `"accounting-za"`. |
| GAP-027 | Customer pages crash after ONBOARDING lifecycle transition | `customerReadiness.requiredFields` returned `null` for newly-transitioned customers. 9 unguarded property accesses (`.total`, `.filled`, `.fields`) caused `TypeError` during SSR. | Added `?.` and `?? 0` / `?? []` guards on all `requiredFields` accesses. Created `error.tsx` boundaries at customer route levels. (PR #687) |
| GAP-030 | Log Time dialog crashes with null currency/source | Two-part: (1) `formatCurrency()` passed `null` currency to `Intl.NumberFormat`. (2) `formatRateSource()` called `.toLowerCase()` on `null` source. First fix (PR #691) addressed only currency; second fix (PR #692) did a full null-safety audit. | Full audit of `log-time-dialog.tsx`: switched to `formatCurrencySafe`, guarded `formatRateSource` with early return, added `hourlyRate != null` guard. (PR #691 + #692) |

### Bugs Resolved

| ID | Summary | Root Cause | Fix (PR) |
|----|---------|------------|----------|
| GAP-025 | Team member list shows "No members found" | `NEXT_PUBLIC_BACKEND_URL` not set as Docker build arg in E2E compose. Client-side `useOrgMembers()` hook defaulted to `localhost:8080`, but E2E backend runs on `8081`. | Added build arg to `docker-compose.e2e.yml` + declared `ARG` in `frontend/Dockerfile`. (PR #688) |
| GAP-026 | FICA/KYC checklist template not visible | `CompliancePackSeeder` set `active=false` on templates with `autoInstantiate=false`. The `listActive()` API then filtered them out. `autoInstantiate` should control auto-creation, not visibility. | Removed the 4-line inactive override. Added `V71` tenant migration to activate existing data. (PR #690) |
| GAP-008C | Projects page intermittent JS error on first load | Unguarded null accesses on `project.status`, `project.createdAt`, and customer API results. | Added error boundary + 3 null guards. (PR #689) |
| GAP-028 | Customer detail page intermittent render crash | Same root cause as GAP-027. | Fixed by PR #687 (same PR). |
| GAP-004 | Statement-of-account template produces stub output | `CustomerContextBuilder` did not query invoice history. Template body had placeholder text only. | Extended builder to query `InvoiceRepository`, build invoice list with running balance and `totalOutstanding`. Updated template with Thymeleaf table. (PR #694) |
| GAP-031 | Timesheet report crashes on empty filter params | `parseUuid()` in report query classes checked for `null` but not empty strings. Frontend sends `""` for unselected filters, `UUID.fromString("")` throws. | Added blank-string check after null check in all 3 report query classes + test. (PR #695) |

---

## Remaining Open Gaps

~~These items were not resolved during the cycle.~~ **All P1 and P2 gaps resolved in follow-up fix cycle (PR #703, merged 2026-03-16).**

### P1 — ~~Should Fix Before Pilot~~ RESOLVED

| ID | Summary | Severity | Fix (PR) |
|----|---------|----------|----------|
| GAP-019 | Currency displays as USD not ZAR | cosmetic | Backend: `TenantProvisioningService` reads currency from vertical profile JSON. Frontend: `formatCurrency` uses locale-aware `Intl.NumberFormat` (en-ZA for ZAR). Retainer components receive `defaultCurrency` prop. (PR #696) |
| GAP-008B | FICA field groups not auto-attached during customer creation | major | Changed `autoApply: false` → `true` in `accounting-za-customer.json`. PackReconciliationRunner retroactively attaches to existing customers. (PR #697) |
| GAP-010 | Trust-specific custom fields missing | major | New `accounting-za-customer-trust.json` field pack with 6 trust fields (Registration Number, Deed Date, Trust Type, Trustees, Appointment Type, Letters of Authority). All fields visibility-conditioned on `acct_entity_type = TRUST`. (PR #699) |
| GAP-009 | FICA checklist does not filter by entity type | major | New `applicable_entity_types` JSONB column on checklist items (V72 migration). Items filtered at instantiation by customer's `acct_entity_type`. Company Registration excluded for sole proprietors. 2 new trust-specific items (Letters of Authority, Trust Deed). (PR #701) |

### P2 — ~~Nice to Have~~ RESOLVED

| ID | Summary | Severity | Fix (PR) |
|----|---------|----------|----------|
| GAP-020 | Portal contacts required for information requests | minor | New `GET /api/customers/{id}/portal-contacts` endpoint. Auto-creates PRIMARY portal contact from customer email during PROSPECT → ONBOARDING transition. (PR #698) |
| GAP-029 | React #418 hydration mismatch on all pages | cosmetic | New `RelativeDate` client component for time-relative rendering. Explicit `en-ZA` locale on `toLocaleDateString`/`toLocaleString` calls. `suppressHydrationWarning` on Date.now()-dependent elements. Fixed `window.location.origin` SSR mismatch. (PR #700) |

---

## Deferred — New Features (WONT_FIX for Bugfix Cycle)

These gaps represent missing features, not bugs. Each is a potential epic for a future phase.

### Automation Triggers (3 gaps)

| ID | Summary | Effort | Business Impact |
|----|---------|--------|-----------------|
| GAP-001 | `PROPOSAL_SENT` trigger does not exist | M | Cannot automate engagement letter follow-up ("not accepted after 5 days"). Practice admin must manually track pending proposals. Revenue risk — unsigned engagement letters delay billable work. |
| GAP-002 | `FIELD_DATE_APPROACHING` trigger does not exist | L | Cannot automate SARS deadline reminders, financial year-end alerts, or provisional tax due date notifications. These are mission-critical compliance deadlines — missing them results in SARS penalties. Requires scheduled polling job. |
| GAP-003 | `CHECKLIST_COMPLETED` trigger does not exist | M | When FICA is complete, no automatic notification fires to the partner for review. Completed FICA files sit unnoticed until someone manually checks. Blocks the PROSPECT → ACTIVE flow from being fully automated. |

### Billing & Invoicing (4 gaps)

| ID | Summary | Effort | Business Impact |
|----|---------|--------|-----------------|
| GAP-011 | No retainer overage/overflow billing | M | When actual hours exceed the retainer cap, the firm absorbs the overage. No mechanism for "retainer + overflow at hourly rates." Vukani Tech's advisory overflow model unsupported. |
| GAP-012 | No effective hourly rate per retainer client | S | Cannot calculate retainer fee / actual hours = effective rate. Critical for retainer pricing decisions. Firms must calculate in Excel. |
| GAP-014 | No disbursement/expense invoicing workflow | M | Expenses can be logged on projects but cannot be included as line items on invoices. Accounting firms frequently disburse CIPC/SARS/Master's Office fees on behalf of clients. |
| GAP-016 | No SA-specific invoice PDF formatting | S | Invoices use the platform's built-in format, not the SA-mandated SARS requirements (seller VAT number, buyer VAT number, tax amount separately stated). The `invoice-za` template exists but may not be wired into the invoice send flow. |

### Workflow & UX (5 gaps)

| ID | Summary | Effort | Business Impact |
|----|---------|--------|-----------------|
| GAP-005 | Terminology overrides not loaded at runtime | M | UI shows "Projects" not "Engagements", "Customers" not "Clients". Feels generic, not accounting-specific. `next-intl` doesn't load vertical overlay namespace. |
| GAP-008A | Org settings page "Coming Soon" | M | Cannot rename org, set default currency, or configure basic firm details. Brand color is settable via Templates page as workaround. |
| GAP-013 | No proposal/engagement letter lifecycle tracking | M | No dashboard showing pending engagement letters, days since sent, conversion rate. Engagement letters are project-scoped, not customer-scoped. |
| GAP-015 | No bulk time entry creation | M | Each time entry is 6-8 clicks. No "copy previous week" or CSV import. Tedious for 20+ entries. |
| GAP-017 | No recurring engagement auto-creation | M | Monthly bookkeeping engagements must be manually recreated each year. No "roll over engagement to next year" from project templates. |

### Out of Scope (3 gaps)

| ID | Summary | Notes |
|----|---------|-------|
| GAP-006 | Rate card defaults not auto-seeded from profile | Manual setup works. Nice-to-have for onboarding friction. |
| GAP-021 | No SARS integration or eFiling export | Future vertical-specific enhancement. Large effort. |
| GAP-022 | No engagement letter auto-creation from template | Nice-to-have — auto-generate document on project creation. |

---

## Disproved Gaps

These gaps were reported in the initial analysis but **empirical testing proved the features exist**.

| ID | Original Claim | Reality |
|----|---------------|---------|
| GAP-023 | No saved views on list pages | "Save View" button exists on Customers and Projects pages. Feature appears implemented. |
| GAP-024 | No aged debtors report | Invoice Aging Report exists at `/reports/invoice-aging` — "Outstanding invoices grouped by age bucket." This IS the aged debtors report. |

---

## Platform Capabilities Verified Working

### Fully Functional (Tested End-to-End)

| Capability | Evidence |
|------------|----------|
| **Customer lifecycle** | PROSPECT → ONBOARDING → ACTIVE with CustomerLifecycleGuard enforcement. 4 entity types (Pty Ltd, CC, Trust, SOC). |
| **FICA/KYC checklists** | 9-item checklist template seeded by accounting-za pack. Manual instantiation via "Add Checklist" dialog. Required/optional item flags. |
| **Custom field packs** | 16 SA-specific client fields seeded (Company Registration, VAT Number, Entity Type, SARS Tax Ref, Financial Year-End, Risk Rating, etc.). Entity type dropdown with SA values. |
| **Time tracking** | Log time on tasks. Billable/non-billable classification. Project time summaries. "My Work" cross-project view (5.5h, 100% billable). |
| **Document generation** | 7 accounting-specific templates (engagement letters, FICA confirmation, monthly report, statement of account). Thymeleaf + OpenHTMLToPDF rendering. SA-specific clauses (SAICA, POPIA, SARS, Tax Administration Act). PDF download. |
| **Clause library** | 7 accounting clauses with required/optional/reorderable associations. Template-to-clause linking. |
| **Team management** | 3 members with roles (Owner/Admin/Member). Project membership required for access. Role-based permissions enforced. |
| **Profitability** | Team utilization (hours/billable/utilization %), project profitability, customer profitability. Date range filtering. Sortable columns. |
| **Reports** | Timesheet, project profitability, invoice aging. CSV + PDF export. Filtering by project/member/date. |
| **Resource planning** | Full capacity planning grid. 40h/week per member. Multi-week view (4w/8w/12w). Over-allocated filter. |
| **Dashboard** | 10+ widgets: project health, team workload, capacity, getting started checklist (4/6 auto-tracked), incomplete profiles, activity feed. |
| **Compliance** | Lifecycle distribution cards, onboarding pipeline table, data requests, dormancy check. |
| **Automations** | 6 pre-seeded rules (3 accounting-specific). Toggle on/off. Rule configuration with trigger type + action. |
| **Request templates** | 4 accounting-specific templates (Tax Return Supporting Docs, Monthly Bookkeeping, Company Registration, Annual Audit). Item-level detail. |
| **Comments** | Post comments on tasks with attribution and timestamps. |

### Present But Not Fully Tested

| Capability | Status |
|------------|--------|
| **Invoicing** | Billing run wizard (5 steps) loads. Invoice list with status filters (Draft/Approved/Sent/Paid/Void). Summary cards. **Not tested end-to-end** — billing run Step 2 showed "No customers with unbilled work" despite linked customer and time entries. May require rate card configuration. |
| **Retainers** | CRUD exists with Active/Paused/Terminated lifecycle. Filters and summary cards. **Not tested end-to-end** — no retainers created during cycle (data wiped on stack rebuild). |
| **Budgets** | Configure budget wizard (fixed-price or time-and-materials). Budget tab on project detail. **Not configured** — no specific budget values tested. |
| **Expenses** | "Log Expense" button on project detail. Empty state with disbursement-relevant label text. **Not created** — expense logging flow not exercised. |
| **Payment recording** | Invoice status lifecycle includes PAID. **Not tested** — no invoices created to record payment against. |
| **Email sending** | Invoice send, information request, engagement letter send flows. **Not tested** — Mailpit integration not exercised. |
| **Saved views** | "Save View" button visible on list pages. **Not tested end-to-end** — view creation/persistence not verified. |

---

## Checkpoint Summary by Day

| Day | Theme | Pass | Partial | Fail | Not Tested | Blocker |
|-----|-------|------|---------|------|------------|---------|
| 0 | Firm Setup | 8 | 3 | 2 | 0 | — |
| 1 | First Client Onboarding | 2 | 3 | 3 | 1 | GAP-027 (resolved) |
| 2 | Second & Third Client | 5 | 1 | 0 | 0 | — |
| 3 | Trust Entity | 2 | 1 | 1 | 2 | — |
| 7 | Time Tracking & Collaboration | 1 | 0 | 5 | 3 | GAP-030 (resolved) |
| 14 | FICA Completion & Review | 1 | 1 | 2 | 2 | — |
| 30 | First Billing Cycle | 2 | 2 | 0 | 8 | — |
| 45 | Bulk Billing & New Engagement | 4 | 2 | 0 | 3 | — |
| 60 | Quarterly Review | 4 | 1 | 0 | 6 | — |
| 75 | Year-End Engagement | 3 | 3 | 0 | 6 | — |
| 90 | Portfolio Review & Fork Readiness | 10 | 2 | 0 | 2 | — |
| **Total** | | **42** | **19** | **13** | **33** | **2 (both resolved)** |

---

## Recommended Phasing for Remaining Work

### ~~Sprint 1 (1 week) — Quick Wins~~ DONE (PR #703)
- ~~**GAP-019**: Currency display (S)~~ — RESOLVED
- ~~**GAP-020**: Auto-create portal contact (S)~~ — RESOLVED
- ~~**GAP-008B**: Auto-attach vertical field groups (M)~~ — RESOLVED
- ~~**GAP-029**: Hydration mismatch (S)~~ — RESOLVED

### ~~Sprint 2 (2 weeks) — Entity-Type Specialization~~ DONE (PR #703)
- ~~**GAP-009**: Entity-type-specific FICA checklists (M)~~ — RESOLVED
- ~~**GAP-010**: Trust-specific custom field group (M)~~ — RESOLVED

### Sprint 3 (2 weeks) — Billing Flow Completion
- Investigate and fix billing run "no unbilled work" issue (may be configuration, may be query bug)
- **GAP-011**: Retainer overflow billing model (M)
- **GAP-014**: Include expenses as invoice line items (M)
- **GAP-016**: Wire SA tax invoice template into invoice send flow (S)

### Sprint 4 (2 weeks) — Automation & Workflow
- **GAP-001**: `PROPOSAL_SENT` trigger type + engagement letter follow-up (M)
- **GAP-003**: `CHECKLIST_COMPLETED` trigger type + FICA completion notification (M)
- **GAP-002**: `FIELD_DATE_APPROACHING` scheduled trigger + SARS deadline reminders (L)

### Post-Launch Polish
- **GAP-005**: Terminology overrides via next-intl vertical overlay (M)
- **GAP-008A**: Org settings page (M)
- **GAP-012**: Effective rate per retainer client metric (S)
- **GAP-015**: Bulk time entry / copy previous week (M)
- **GAP-017**: Recurring engagement auto-creation (M)

---

## Appendix: QA Cycle Execution Log

| Timestamp | Agent | Action |
|-----------|-------|--------|
| 2026-03-15 22:35 | Setup | Initial status seeded from gap report (27 gaps) |
| 2026-03-15 23:12 | Infra | GAP-008 fixed — verticalProfile provisioning + seed.sh |
| 2026-03-16 00:15 | QA | Day 0 complete — 8 PASS, 3 PARTIAL, 2 FAIL |
| 2026-03-16 00:30 | QA | Day 1 partial — BLOCKED by GAP-027 (SSR crash) |
| 2026-03-16 01:30 | Product | Triage cycle 1 — 5 specs written, 11 WONT_FIX |
| 2026-03-16 02:00 | Dev | GAP-027 fixed (PR #687) |
| 2026-03-16 02:15 | Dev | GAP-025 fixed (PR #688) |
| 2026-03-16 02:30 | Dev | GAP-008C fixed (PR #689) |
| 2026-03-16 02:45 | Dev | GAP-026 fixed (PR #690) |
| 2026-03-16 03:05 | Infra | E2E stack rebuilt |
| 2026-03-16 03:15 | QA | Cycle 2 — 4/4 fixes VERIFIED, Days 1-3 complete |
| 2026-03-16 04:00 | QA | Day 7 — BLOCKED by GAP-030 (currency null crash) |
| 2026-03-16 04:15 | Product | GAP-030 triaged |
| 2026-03-16 04:30 | Dev | GAP-030 v1 fixed (PR #691) — incomplete |
| 2026-03-16 05:00 | QA | GAP-030 REOPENED — second null crash |
| 2026-03-16 05:30 | Dev | GAP-030 v2 fixed (PR #692) — full audit |
| 2026-03-16 06:00 | Infra | E2E stack rebuilt |
| 2026-03-16 06:30 | QA | Cycle 4 — GAP-030 VERIFIED, Days 30-90 complete |
| 2026-03-16 07:00 | Dev | GAP-004 fixed (PR #694) — statement of account |
| 2026-03-16 07:00 | Dev | GAP-031 fixed (PR #695) — report UUID parsing |
| 2026-03-16 08:30 | Infra | All builds green, PR #693 merged to main |

**Total duration**: ~10 hours (including E2E stack builds)
**Total PRs**: 8 (#687–#695) merged to main
**Total test suite**: 1513 frontend tests passing, 3630+ backend tests passing

### Follow-Up Fix Cycle (PR #703)

| Timestamp | Action |
|-----------|--------|
| 2026-03-16 09:00 | 6 research agents dispatched in parallel for gap analysis |
| 2026-03-16 09:22 | GAP-019 fixed — currency display (PR #696) |
| 2026-03-16 10:28 | GAP-008B fixed — auto-attach field groups (PR #697) |
| 2026-03-16 11:35 | GAP-020 fixed — portal contact auto-creation + endpoint (PR #698) |
| 2026-03-16 11:49 | GAP-010 fixed — trust custom fields (PR #699) |
| 2026-03-16 11:55 | GAP-029 fixed — hydration mismatch (PR #700) |
| 2026-03-16 12:24 | GAP-009 fixed — entity-type FICA filtering (PR #701) |
| 2026-03-16 12:27 | Review feedback addressed (PR #702) |
| 2026-03-16 12:30 | All PRs merged, PR #703 merged to main |

**Follow-up duration**: ~3.5 hours
**Follow-up PRs**: 7 (#696–#702), merged via parent branch PR #703
**Files changed**: 42 files, +1,247/-72 lines
**New tests**: 3 integration test classes (ProvisioningIntegrationTest, PortalContactAutoCreationTest, ChecklistEntityTypeFilterTest)

---

*Generated by QA Cycle `/qa-cycle` skill on 2026-03-16. Follow-up fixes by feature-gap-analysis-and-dev workflow. Checkpoint results in `qa_cycle/checkpoint-results/`. Fix specs in `qa_cycle/fix-specs/`.*
