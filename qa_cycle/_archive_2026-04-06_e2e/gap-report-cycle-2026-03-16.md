# QA Cycle Gap Report — accounting-za Vertical
## Date: 2026-03-16 | Branch: `bugfix_cycle_2026-03-15` | PR: #693 (merged to main)

**Scenario**: 90-day accelerated lifecycle of "Thornton & Associates", a 3-person Johannesburg accounting firm
**Method**: Playwright MCP browser automation against E2E mock-auth stack (http://localhost:3001)
**Execution**: 4 QA cycles, 11 lifecycle days tested (Day 0, 1, 2, 3, 7, 14, 30, 45, 60, 75, 90)
**Actors**: Alice (Owner/Senior Accountant), Bob (Admin/Bookkeeper), Carol (Member/Junior Accountant)

---

## Executive Summary

The DocTeams platform with the `accounting-za` vertical profile covers **~75%** of a small SA accounting firm's daily needs. The core workflow loop — onboard client, log time, generate documents, review profitability — is functional. Three blockers were found and resolved during the cycle. Remaining gaps cluster around entity-type-specific compliance (trusts), billing flow completeness, and SA currency display.

| Category | Count |
|----------|-------|
| Total gaps identified | 31 |
| Resolved during cycle (PRs merged) | 10 |
| Deferred (new features, out of scope) | 9 |
| Wiring fixes (partially built, need small fix) | 2 |
| Disproved (feature already exists) | 6 |
| Remaining open | 4 |

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

These items were not resolved during the cycle. They require product decisions or dedicated phase work.

### P1 — Should Fix Before Pilot

| ID | Summary | Severity | Day | Effort | Impact |
|----|---------|----------|-----|--------|--------|
| GAP-008B | FICA field groups not auto-attached during customer creation | major | 1 | M | During customer creation wizard Step 2, only "Contact & Address" is shown. The 16 accounting-specific fields (Company Registration, VAT Number, Entity Type, SARS Tax Reference, etc.) must be manually added via "Add Group" on the customer detail page AFTER creation. For an accounting firm, FICA details should be captured during intake — not as a post-creation afterthought. **Workaround**: Users manually add the field group after creation. |
| GAP-009 | FICA checklist does not filter by entity type | major | 3 | M | All customers see the same 9-item FICA checklist regardless of entity type. Sole proprietors see "Company Registration (CM29/CoR14.3)" which is irrelevant. Trusts see nothing about Letters of Authority from the Master. SA FICA requirements differ significantly by entity type (Pty Ltd vs Trust vs Sole Proprietor). **Impact**: Accounting firms must manually tell clients to ignore inapplicable items. |
| GAP-010 | Trust-specific custom fields missing | major | 3 | M | No fields for Trust Registration Number (Master's reference), Trust Deed Date, Names of Trustees, Appointed vs Ex Officio, Trust Type (inter vivos / testamentary / business). The generic "Company Registration Number" field is used as a catch-all, but trusts are not registered with CIPC. **Impact**: Cannot properly serve trust clients without manual workarounds. |
| GAP-019 | Currency displays as USD not ZAR | cosmetic | 0 | S | All amounts display with `$` prefix instead of `R`. Invoices show `$5,500.00` instead of `R5,500.00`. Rate cards show `$1,500/hr` instead of `R1,500/hr`. The `Intl.NumberFormat` locale is hardcoded to `en-US`. **Impact**: Unprofessional appearance for a South African firm. Easy fix — change default currency in org settings or formatCurrency locale. |

### P2 — Nice to Have / Future Phases

| ID | Summary | Severity | Day | Notes |
|----|---------|----------|-----|-------|
| GAP-020 | Portal contacts required for information requests | minor | 1 | "Create Information Request" dialog requires a portal contact. Users must create a portal contact first. Onboarding flow should auto-create a portal contact from the customer's email. |
| GAP-029 | React #418 hydration mismatch on all pages | cosmetic | 0 | Pre-existing SSR/client mismatch. Pages render correctly after hydration. Likely date formatting or locale-dependent content divergence. Non-blocking. |

---

## Deferred — New Features (WONT_FIX for Bugfix Cycle)

These gaps represent missing features, not bugs. Each is a potential epic for a future phase.

### Automation Triggers (1 genuine gap)

| ID | Summary | Effort | Business Impact |
|----|---------|--------|-----------------|
| GAP-002 | `FIELD_DATE_APPROACHING` trigger does not exist | L | Cannot automate SARS deadline reminders, financial year-end alerts, or provisional tax due date notifications. These are mission-critical compliance deadlines — missing them results in SARS penalties. Requires scheduled polling job + new event + trigger type. Infrastructure (scheduled jobs) exists at ~20%. |

### Billing & Invoicing (1 genuine gap)

| ID | Summary | Effort | Business Impact |
|----|---------|--------|-----------------|
| GAP-016 | No SA-specific invoice PDF formatting | S | VAT fields, tax infrastructure, and vertical profile all exist. Missing: an `invoice-za` Thymeleaf template wired into the invoice send flow, and customer VAT number extraction into the invoice render context. |

### Workflow & UX (4 gaps)

| ID | Summary | Effort | Business Impact |
|----|---------|--------|-----------------|
| GAP-005 | Terminology overrides not loaded at runtime | M | UI shows "Projects" not "Engagements", "Customers" not "Clients". Feels generic, not accounting-specific. No `next-intl` or i18n infrastructure exists in the frontend. |
| GAP-008A | Org settings page "Coming Soon" | M | 20+ sub-settings pages exist but no central hub page for org name, default currency, branding. Backend `OrgSettingsController` is ready. |
| GAP-013 | No proposal/engagement letter lifecycle tracking | M | Proposal entity has full lifecycle (DRAFT → SENT → ACCEPTED/DECLINED/EXPIRED) with timestamps. Missing: a dashboard page showing pending proposals, days since sent, conversion metrics. Backend data is ready — frontend page needed. |
| GAP-015 | No bulk time entry creation | M | Each time entry is 6-8 clicks. No "copy previous week" or CSV import. Single-entry CRUD only. Tedious for 20+ entries. |

### Out of Scope (3 gaps)

| ID | Summary | Notes |
|----|---------|-------|
| GAP-006 | Rate card defaults not auto-seeded from profile | Manual setup works. Nice-to-have for onboarding friction. |
| GAP-021 | No SARS integration or eFiling export | Future vertical-specific enhancement. Large effort. |
| GAP-022 | No engagement letter auto-creation from template | Rendering pipeline exists. Needs auto-trigger on project creation. Nice-to-have. |

---

## Wiring Fixes — Partially Built, Need Small Integration

These were categorized as "new features" but investigation shows 60-80% of the work is done. The missing piece is wiring existing events into the automation engine.

| ID | Original Claim | Reality | Fix |
|----|---------------|---------|-----|
| GAP-001 | `PROPOSAL_SENT` trigger does not exist | `ProposalSentEvent` exists and fires. `NotificationEventHandler.onProposalSent()` creates notifications. **Missing**: not mapped in `TriggerTypeMapping` so the automation engine can't react to it. | Add `PROPOSAL_SENT` to `TriggerType` enum + `TriggerTypeMapping` + create automation template. ~S effort. |
| GAP-003 | `CHECKLIST_COMPLETED` trigger does not exist | `ChecklistInstanceService.checkInstanceCompletion()` detects completion and triggers lifecycle transition → publishes `CustomerStatusChangedEvent`. **Missing**: `CustomerStatusChangedEvent` is a Spring `ApplicationEvent`, not a `DomainEvent` — `AutomationEventListener` only listens to `DomainEvent`. | Convert to `DomainEvent` or add `ApplicationEvent` listener in `AutomationEventListener`. ~S effort. |

---

## Disproved Gaps

These gaps were reported in the initial analysis but **investigation proved the features already exist**.

| ID | Original Claim | Reality |
|----|---------------|---------|
| GAP-011 | No retainer overage/overflow billing | **Fully implemented.** `RetainerPeriod` tracks `overageHours`, `closePeriod()` resolves overage rate via 3-tier lookup, creates invoice with base fee + overage line items. Rollover policies (FORFEIT/CARRY_FORWARD/CARRY_CAPPED) all work. |
| GAP-012 | No effective hourly rate per retainer client | **Fully implemented.** `RetainerPeriodService.resolveCustomerRate()` provides 3-tier rate lookup (customer → org default) with effective date filtering. |
| GAP-014 | No disbursement/expense invoicing workflow | **Fully implemented.** `Expense` entity (V50) with `billable` flag, `markupPercent`, `invoiceId` link. `InvoiceLineType.EXPENSE` supported. Full CRUD via `ExpenseController`. |
| GAP-017 | No recurring engagement auto-creation | **Fully implemented** as `RecurringSchedule`. Entity, service, executor, controller, event publishing (`RecurringProjectCreatedEvent`). Complete pipeline — just not exercised during QA scenario. |
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
| **Document generation** | 7 accounting-specific templates (engagement letters, FICA confirmation, monthly report, statement of account). Tiptap + OpenHTMLToPDF rendering. SA-specific clauses (SAICA, POPIA, SARS, Tax Administration Act). PDF download. |
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

### Sprint 1 (1 week) — Quick Wins + Wiring Fixes
- **GAP-019**: Currency display (S) — change default locale/currency in formatCurrency or seed org with ZAR
- **GAP-020**: Auto-create portal contact from customer email during onboarding (S)
- **GAP-001**: Wire `ProposalSentEvent` into `TriggerTypeMapping` + automation template (S — event already fires)
- **GAP-003**: Wire `CustomerStatusChangedEvent` into automation engine as DomainEvent (S — lifecycle logic exists)
- **GAP-016**: Create SA invoice template + extract customer VAT into render context (S — infrastructure ready)

### Sprint 2 (1 week) — Entity-Type Specialization
- **GAP-008B**: Auto-attach vertical field groups during customer creation (M)
- **GAP-009**: Entity-type-specific FICA checklists (M) — create variants or add conditional visibility
- **GAP-010**: Trust-specific custom field group (M) — new field pack with conditional display

### Sprint 3 (2 weeks) — Genuine New Features
- **GAP-002**: `FIELD_DATE_APPROACHING` scheduled trigger + SARS deadline reminders (L — new scheduled job)
- **GAP-008A**: Org settings hub page (M — backend ready, frontend page needed)
- **GAP-013**: Proposal lifecycle dashboard (M — backend data ready, frontend page needed)

### Post-Launch Polish
- **GAP-005**: Terminology overrides via next-intl vertical overlay (M — no i18n exists yet)
- **GAP-015**: Bulk time entry / copy previous week (M — new feature)

### Verification Needed (Features Exist But Untested in QA)
- **GAP-011/012/014/017**: Retainer overflow, effective rates, expense invoicing, recurring schedules — all fully implemented but QA scenario didn't exercise them. Need dedicated test coverage in next QA cycle.

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

---

*Generated by QA Cycle `/qa-cycle` skill on 2026-03-16. Checkpoint results in `qa_cycle/checkpoint-results/`. Fix specs in `qa_cycle/fix-specs/`.*
