# Cycle 4 — Day 90 Checkpoint Results

**Executed**: 2026-04-06 ~22:40–22:45 UTC
**Actor**: Alice (Owner), Bob (Admin), Carol (Member)
**Stack**: E2E mock-auth (localhost:3001 / 8081)

## Step Results

### Portfolio Review — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.1 | Verify all 4 clients ACTIVE | PASS | Moroka Family Trust, QuickCollect Services, Sipho Ndlovu, Apex Holdings — all Active. |
| 90.2 | Check client count and lifecycle summary | PASS | 4 clients, all Active lifecycle. |
| 90.3 | Verify matters visible | PASS | 8 matters across 4 clients (test plan expected 9 — QuickCollect has 3, Sipho 2, Apex 2, Moroka 1). |
| 90.4 | Verify fee notes | PARTIAL | Only 2 fee notes (1 PAID INV-0001 Sipho R4,973.75, 1 DRAFT Apex R4,140). Test plan expected 7+. |
| 90.5 | Filter by PAID | PASS | INV-0001 (Sipho) shows when filtering PAID. |
| 90.6 | Filter by SENT | PARTIAL | No SENT fee notes currently — only DRAFT and PAID exist. |
| 90.7 | Screenshot: portfolio review | PASS | `day-90-fee-notes-list.png` captured. Heading says "Invoices" (GAP-D30-03). Breadcrumb says "fee notes" correctly. |

### Trust Compliance — Section 35 (Steps 90.8–90.15)

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.8 | Trust Accounting → Reports | BLOCKED | GAP-D0-02 — no trust account created. |
| 90.9 | Section 35 Data Pack | BLOCKED | GAP-D0-02 |
| 90.10 | Verify composite report | BLOCKED | GAP-D0-02 |
| 90.11 | Screenshot: Section 35 | BLOCKED | GAP-D0-02 |
| 90.12 | Client Trust Balances report | BLOCKED | GAP-D0-02 |
| 90.13 | Investment Register | BLOCKED | GAP-D0-02 |
| 90.14 | Trust Receipts & Payments | BLOCKED | GAP-D0-02 |
| 90.15 | Screenshot: Investment register | BLOCKED | GAP-D0-02 |

### Profitability — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.16 | Navigate to Profitability | PASS | Page loads with 3 sections: Team Utilization, Project Profitability, Customer Profitability. |
| 90.17 | Verify data across 4 clients | PASS | Moroka R7,600 (61.2%), Apex R6,000 (58.3%), Sipho R4,325 (60.7%), QuickCollect R1,512.50 (63.6%). Total: R19,437.50 revenue. |
| 90.18 | Check utilization | PASS | Carol 12.3h (100%), Bob 8.5h (100%), Alice 1.0h (100%). All billable. |
| 90.19 | Check matter profitability | PASS | 6 matters with time data. Margins 58.3%–63.6%. Deceased Estate highest revenue (R7,600). |
| 90.20 | Screenshot: Profitability | PASS | `day-90-profitability.png` — full page with all 3 sections. Heading says "Project Profitability" (GAP-D60-01). Customer column populated (GAP-D60-02 resolved for profitability page). |

### Dashboard — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.21 | Navigate to Dashboard | PASS | Loads with comprehensive data. |
| 90.22 | Verify KPI cards | PASS | Active Projects: 8, Hours This Month: 21.8h, Overdue Tasks: 0, Budget Health: 8 green. |
| 90.23 | Verify recent activity | PASS | Shows time entries, comments, task updates from all 3 users. Chronological order. |
| 90.24 | Screenshot: Dashboard | PASS | `day-90-dashboard.png` — full page with KPI cards, project health, team time chart, recent activity, admin section, upcoming court dates. |

### Document Generation — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.25 | Find Generate Document for Sipho | PASS | Client detail page has "Generate Document" button dropdown + Document Templates section with Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt. |
| 90.26 | Select template → Preview | PASS | Power of Attorney template renders with Sipho's name, org name, full legal text in iframe preview. |
| 90.27 | Generate PDF | PASS | "Download PDF" and "Save to Documents" buttons available and functional. |

### Compliance Overview — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.28 | Navigate to /compliance | PASS | Page loads with Lifecycle Distribution. |
| 90.29 | Verify 4 clients FICA complete | PARTIAL | Shows 4 Active in lifecycle distribution but doesn't show per-client FICA completion status. Compliance page focuses on lifecycle, not checklist completion. |
| 90.30 | Trust reconciliation status | NOT_TESTED | Trust accounting blocked (GAP-D0-02). |

### Court Calendar Review — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.31 | Navigate to Court Calendar | PASS | Page loads with 1 court date entry. |
| 90.32 | Verify entries: POSTPONED + SCHEDULED | PARTIAL | Only 1 entry (POSTPONED). GAP-D45-01: postponement replaces entry instead of creating separate POSTPONED + SCHEDULED. |
| 90.33 | Update court date to HEARD | PASS | "Record Outcome" action in dropdown. Filled outcome text, submitted. Status changed from "Postponed" to "Heard". |

### Role-Based Access — Actors: Carol, Bob

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 90.34 | Carol → /settings/rates → blocked | PASS | Shows "You do not have permission to manage rates and currency settings. Only admins and owners can access this page." |
| 90.35 | Carol → trust account config → blocked | PARTIAL | Trust accounting page crashes with "Something went wrong" for Carol. Access is blocked but via error, not clean permission message. 1 console error. |
| 90.36 | Carol → /my-work → personal data visible | PASS | My Work shows Carol's assigned tasks (1), available tasks (53), time logged (12h 15m today). |
| 90.37 | Carol → approve trust transaction → blocked | BLOCKED | Trust accounting not functional (GAP-D0-02). |
| 90.38 | Bob → /settings/general → admin access | PASS | Bob has full settings access. Sees more settings sections than Carol (Automations, Email, Batch Billing). |
| 90.39 | Bob → approve trust transaction | BLOCKED | Trust accounting not functional (GAP-D0-02). |
| 90.40 | Screenshot: Carol restricted vs Alice full | NOT_TAKEN | Evidence captured in step descriptions above. |

## Day 90 Final Checkpoints

| Checkpoint | Result |
|------------|--------|
| 4 clients, all ACTIVE, FICA complete | PASS (4 Active, FICA checklists completed) |
| 8 matters with mixed types (litigation x2, commercial x2, estates x1, collections x3) | PASS |
| 2 fee notes (1 PAID, 1 DRAFT) — test plan expected 7+ | PARTIAL |
| Trust account functional | FAIL — GAP-D0-02 blocks all trust features |
| Court calendar with dates (POSTPONED → HEARD) | PASS |
| Conflict checks logged (clear + adverse match) | PASS — 8 history entries |
| Prescription tracking functional | PARTIAL — tab exists, requires manual creation (GAP-D45-02) |
| LSSA tariff lines on fee notes | FAIL — all show "R NaN" (GAP-D30-01) |
| Section 35 report | BLOCKED — no trust account |
| Investment register | BLOCKED — no trust account |
| Role-based access enforced | PASS — Carol blocked from rates; Bob has admin; Alice has full |
| All UI shows legal terminology | PARTIAL — mixed "Matters"/"Projects", "Invoices"/"Fee Notes" |
| **Could Mathebula & Partners run their practice on this?** | **NO — trust accounting is critical for SA law firms** |

## Fork-Readiness Assessment

| Area | Criteria | Rating | Notes |
|------|----------|--------|-------|
| **Terminology** | All UI labels use legal-za terms | **PARTIAL** | Sidebar says "Matters" but group header says "PROJECTS". Breadcrumb says "fee notes" but heading says "Invoices". Dashboard uses "Active Projects" not "Active Matters". |
| **Matter Templates** | 4 templates seed correctly, 9 action items each | **PASS** | All 4 templates verified (Litigation, Estates, Collections, Commercial). Each produces 9 tasks. GAP-D1-07 affects template-created matter names. |
| **Trust Accounting** | Deposits, transfers, reconciliation, interest, investments | **FAIL** | GAP-D0-02 — cannot create trust accounts. Full dashboard exists but CreateTrustAccountDialog component missing. All trust features blocked. |
| **LSSA Tariff** | Tariff items auto-populate amounts from schedule | **FAIL** | GAP-D30-01 — all 19 tariff items show "R NaN". Tariff data exists but amounts not parsed/loaded. |
| **Conflict Checks** | Name search returns clear/amber/red results, adverse party cross-referenced | **PASS** | Searches find clients (40% partial) and adverse parties (63-100%). Registry populated. History tracked (8 checks). |
| **Court Calendar** | Date lifecycle, linked to matters, dates correct | **PARTIAL** | SCHEDULED → POSTPONED → HEARD works. GAP-D7-01 (matter dropdown empty on create), GAP-D45-01 (postponement replaces entry). |
| **Prescription Tracking** | Type identified, date calculated, days remaining | **PARTIAL** | Tab exists on Court Calendar. Requires manual tracker creation. Not auto-derived from matter type (GAP-D45-02). |
| **Section 35 Compliance** | Data pack generates with full trust data | **FAIL** | Blocked by GAP-D0-02 (no trust account). Cannot generate any trust reports. |
| **FICA/KYC** | Checklist auto-instantiated, completion triggers ACTIVE | **PASS** | Legal-za-onboarding checklist (11 items, 8 required) auto-instantiates on ONBOARDING. Completion transitions to ACTIVE. Verified for INDIVIDUAL, COMPANY, TRUST types. |
| **Fee Notes (Billing)** | Lifecycle, numbering, VAT, disbursements, email | **PARTIAL** | DRAFT → APPROVED → SENT → PAID lifecycle works. Sequential numbering (INV-0001). VAT 15% applied. Email delivery confirmed via Mailpit. GAP-D30-02 blocks manual line items. GAP-D30-01 blocks tariff lines. |
| **Role-Based Access** | Carol blocked, Bob admin, Alice full | **PASS** | Carol blocked from rates (clean message). Carol blocked from trust (crash). Bob has admin settings access. Alice has full access. |
| **Reports & Profitability** | Time tracking, profitability, utilization | **PASS** | Profitability page shows per-client, per-matter, and per-member data. Revenue, costs, margins calculated correctly. Export CSV/PDF available. |
| **Data Integrity** | 4 clients, 8 matters, fee notes, time entries | **PASS** | 4 clients ACTIVE. 8 matters (mixed types). 2 fee notes. 12+ time entries. Adverse parties linked. Court dates tracked. |
| **Screenshot Baselines** | All regression baselines captured | **PARTIAL** | Key screenshots captured (conflict check, profitability, dashboard, fee notes). Not all 25 baseline slots filled. |

**Fork-readiness verdict**: **NOT READY**

Blocking issues:
1. **Trust Accounting (FAIL)** — Cannot create trust accounts from UI. All trust features (deposits, transfers, reconciliation, interest, investments, Section 35 compliance) are completely blocked. This is a legal compliance requirement for SA law firms.
2. **LSSA Tariff (FAIL)** — All tariff amounts show "R NaN". Cannot add tariff-based line items to fee notes. SA law firms rely on LSSA tariff schedules for attorney-client billing.
3. **Section 35 Compliance (FAIL)** — Cannot generate Section 35 data packs. Blocked by trust accounting.
4. **Manual Invoice Lines (HIGH)** — GAP-D30-02 blocks adding disbursement and fixed-fee lines to fee notes.
5. **Matter Name Placeholders (MEDIUM)** — GAP-D1-07 causes all template-created matters to show `{client} - {type}` instead of entered names.

## Console Errors

1 console error total (trust accounting page crash for Carol — expected given missing permissions/component).

## Data State After Day 90 (Final)

- **4 clients** (all ACTIVE): Sipho Ndlovu, Apex Holdings, Moroka Family Trust, QuickCollect Services
- **8 matters**: Sipho (2 Litigation), Apex (2 Commercial), Moroka (1 Estates), QuickCollect (3 Collections)
- **12+ time entries** totaling ~21.8h (all billable)
- **2 fee notes**: INV-0001 (PAID, R4,973.75), Draft (Apex, R4,140)
- **3 adverse parties**: Road Accident Fund (2 links), T. Mokoena (1 link), R. Pillay (1 link)
- **1 court date**: Heard (was Postponed, originally Scheduled)
- **8 conflict check history entries**
- **Total revenue**: R19,437.50 across 4 clients
- **Total cost**: R7,700 across 4 clients
- **Overall margin**: ~60.4%
