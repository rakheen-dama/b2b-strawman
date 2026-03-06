# DocTeams QA Test Plan — Master Overview

**Product:** DocTeams — Multi-tenant B2B SaaS Practice Management Platform
**Version:** Post-Phase 34 (all features through Information Requests)
**Date:** 2026-03-06
**Prepared by:** QA Team

---

## Scope

This test plan covers all business features of DocTeams from the perspective of an organization with 10 members. Multi-tenancy infrastructure (schema isolation, provisioning) is **out of scope** — we assume a single, fully-provisioned organization.

### In Scope
- All CRUD operations across 22+ domain entities
- Business logic: state machines, lifecycle guards, calculations
- Data accuracy: dashboards, reports, aggregations, financial calculations
- RBAC: role-based access control across Owner/Admin/Member
- Cross-feature integration: invoice generation from time entries, proposal-to-project, etc.
- Customer portal: magic-link auth, limited access, document acceptance
- Settings & configuration: rates, templates, custom fields, tags, compliance packs
- Data integrity: referential integrity, concurrent edits, cascading effects
- Notification triggers: correct events fire correct notifications

### Out of Scope
- Multi-tenancy isolation (schema-per-tenant)
- Plan tier enforcement (Starter vs Pro)
- Clerk webhook sync mechanics
- Infrastructure (S3, LocalStack, Docker)
- Performance/load testing
- Browser compatibility matrix

---

## Test Team

| # | Name | Email | Org Role | Primary Test Domains |
|---|------|-------|----------|---------------------|
| 1 | Thandi Nkosi | thandi@docteams.com | Owner | Settings, billing config, org-level reports, owner-only actions |
| 2 | James Chen | james@docteams.com | Admin | Customer lifecycle, invoicing, proposals, admin workflows |
| 3 | Priya Sharma | priya@docteams.com | Admin | Templates, clauses, document generation, compliance |
| 4 | Marcus Webb | marcus@docteams.com | Admin | Rates, budgets, profitability reports, retainers |
| 5 | Sofia Reyes | sofia@docteams.com | Member | Project management, task workflows, time tracking |
| 6 | Aiden O'Brien | aiden@docteams.com | Member | My Work, calendar, expenses, comments |
| 7 | Yuki Tanaka | yuki@docteams.com | Member | Document uploads, portal features, notifications |
| 8 | Fatima Al-Hassan | fatima@docteams.com | Member | Custom fields, tags, saved views, filtering |
| 9 | David Molefe | david@docteams.com | Member | Cross-project scenarios, bulk operations, edge cases |
| 10 | Lerato Dlamini | lerato@docteams.com | Member | Negative testing, RBAC boundaries, error paths |

---

## Test Customers

| Customer | Lifecycle Status | Purpose |
|----------|-----------------|---------|
| Acme Corp | ACTIVE | Primary happy-path customer; has projects, invoices, retainer, portal contacts |
| Bright Solutions | PROSPECT | Lifecycle guard testing — blocked from project/invoice creation |
| Crestview Holdings | ONBOARDING | Checklist completion and auto-transition to ACTIVE |
| Dunbar & Associates | ACTIVE | Second active customer for cross-customer reports and rate overrides |
| Echo Ventures | DORMANT | Dormancy detection, reactivation flows |
| Fable Industries | OFFBOARDING | Offboarding flow, data retention, restricted operations |

### Portal Contacts

| Contact | Customer | Role | Purpose |
|---------|----------|------|---------|
| alice.porter@acmecorp.com | Acme Corp | PRIMARY | Main portal testing — proposals, documents, info requests |
| ben.finance@acmecorp.com | Acme Corp | BILLING | Invoice viewing, payment flows |
| carol.ops@dunbar.com | Dunbar & Associates | GENERAL | Multi-customer portal testing |

---

## Test Plan Index

### Layer 1: End-to-End Business Journeys
| File | Description |
|------|-------------|
| [01-e2e-journeys.md](01-e2e-journeys.md) | 8 cross-domain business journeys covering the full client engagement lifecycle |

### Layer 2: Domain Deep-Dives (Full Executable Detail)
| File | Description |
|------|-------------|
| [02-invoicing-billing.md](02-invoicing-billing.md) | Invoice CRUD, line items, tax calculations, unbilled time generation, status lifecycle, PDF preview, payment |
| [03-rate-cards-budgets.md](03-rate-cards-budgets.md) | 3-level rate hierarchy, effective dates, cost rates, budget tracking, alerts, profitability reports |
| [04-customer-lifecycle.md](04-customer-lifecycle.md) | State machine (PROSPECT through OFFBOARDED), lifecycle guards, checklists, dormancy, completeness scores |
| [05-proposals-retainers.md](05-proposals-retainers.md) | Proposal pipeline (DRAFT to ACCEPTED), project creation, retainer agreements, periods, rollover |
| [06-document-generation-acceptance.md](06-document-generation-acceptance.md) | Templates, clause injection, context assembly, PDF rendering, acceptance requests, e-signing audit trail |

### Layer 2: Domain Deep-Dives (Scenario Outlines)
| File | Description |
|------|-------------|
| [07-projects-tasks-time.md](07-projects-tasks-time.md) | Project CRUD & lifecycle, task management, time entry logging, My Work view |
| [08-documents-comments-notifications.md](08-documents-comments-notifications.md) | Document upload/scoping, comments & threading, notification triggers & preferences |
| [09-portal-compliance-audit.md](09-portal-compliance-audit.md) | Customer portal access, magic-link auth, compliance/data requests, audit event coverage |
| [10-settings-customfields-tags.md](10-settings-customfields-tags.md) | Org settings, custom field definitions/groups, tags, saved views, project templates, schedules |

### Layer 3: Cross-Cutting Concerns
| File | Description |
|------|-------------|
| [11-cross-cutting-concerns.md](11-cross-cutting-concerns.md) | RBAC matrix, data integrity, optimistic locking, cascading effects, notification completeness |

---

## Test Data Conventions

- **Currency:** ZAR (South African Rand) as org default, USD for cross-currency test cases
- **Dates:** Use relative dates (today, today+7, today+30) in test cases
- **Rate values:** Use round numbers for easy manual verification (e.g., R500/hr, R300/hr cost)
- **Time entries:** Use 60-minute and 120-minute durations for easy hour calculations
- **Invoice amounts:** Designed so line item totals are easily verifiable by hand

## Severity Definitions

| Severity | Definition | Example |
|----------|-----------|---------|
| **Critical** | Financial calculation error, data loss, security bypass | Invoice total wrong, RBAC bypass, data leaking between portal contacts |
| **High** | Feature broken, workflow blocked, incorrect report data | Cannot approve invoice, profitability numbers wrong, lifecycle guard not enforcing |
| **Medium** | Feature partially working, workaround available | Notification not sent but audit logged, filter not applied correctly |
| **Low** | Cosmetic, minor UX issue, non-blocking | Date format inconsistency, sort order unexpected, tooltip missing |
