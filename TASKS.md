# Multi-tenant SaaS Starter — Technical Task Breakdown

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices   | Status   |
|------|------|-------|------|--------|----------|----------|
| **Phase 1 — Core Platform** | | | | | | See [tasks/phase1-core-platform.md](tasks/phase1-core-platform.md) |
| 1 | Scaffolding & Local Dev | Both | — | M | —        | **Done** |
| 2 | Auth & Clerk Integration | Frontend | 1 | M | —        | **Done** |
| 3 | Organization Management | Frontend | 2 | S | —        | **Done** |
| 4 | Webhook Infrastructure | Frontend | 1, 2 | M | 4A, 4B   | **Done** |
| 5 | Tenant Provisioning | Backend | 1, 6 | L | 5A, 5B, 5C | **Done** |
| 6 | Multitenancy Backend | Backend | 1 | L | —        | **Done** |
| 7 | Core API — Projects | Backend | 6 | M | 7A, 7B   | **Done** |
| 8 | Core API — Documents | Backend | 7, 9 | M | 8A, 8B   | **Done** |
| 9 | S3 Integration | Backend | 1 | S | —        | **Done** |
| 10 | Dashboard & Projects UI | Frontend | 3, 7 | M | 10A, 10B, 10C | **Done** |
| 11 | Documents UI | Frontend | 10, 8 | M | 11A, 11B | **Done** |
| 12 | Team Management UI | Frontend | 3 | S | —        | **Done** |
| 13 | Containerization | Both | 1 | S | —        | **Done** |
| 14 | AWS Infrastructure | Infra | 13 | XL | 14A–14D  | **Done** |
| 15 | Deployment Pipeline | Infra | 13, 14 | L | 15A, 15B | **Done** |
| 16 | Testing & Quality | Both | 7, 8, 10, 11 | L | 16A–16C  |          |
| 17 | Members Table + Webhook Sync | Both | 4, 5 | M | 17A, 17B | **Done** |
| 18 | MemberFilter + MemberContext | Backend | 17 | M | 18A, 18B | **Done** |
| 19 | Project Members Table + API | Backend | 18 | M | 19A, 19B | **Done** |
| 20 | Project Access Control | Backend | 19 | L | 20A, 20B | **Done** |
| 21 | Frontend — Project Members Panel | Frontend | 19, 20 | M | 21A, 21B | **Done** |
| 22 | Frontend — Filtered Project List | Frontend | 20, 21 | S | —        | **Done** |
| **Phase 2 — Billing & Tiered Tenancy** | | | | | | See [tasks/phase2-billing-tiered-tenancy.md](tasks/phase2-billing-tiered-tenancy.md) |
| 23 | Tier Data Model & Plan Sync | Both | — | M | 23A, 23B | **Done** |
| 24 | Shared Schema & Row-Level Isolation | Backend | 23 | L | 24A, 24B, 24C | **Done** |
| 25 | Plan Enforcement | Both | 24 | S | — | **Done** |
| 26 | Billing UI & Feature Gating | Frontend | 23 | M | 26A, 26B | **Done** |
| 27 | Tier Upgrade — Starter to Pro | Backend | 23, 24 | M | — | Done (PR #53) |
| **Change Request — Self-Managed Subscriptions** | | | | | |          |
| 28 | Self-Hosted Subscriptions & Clerk Billing Removal | Both | 23, 25 | M | 28A, 28B, 28C | **Done** |
| 29 | Self-Service Plan Upgrade (Simulated) | Both | 28 | S | 29A, 29B | **Done** |
| **Phase 3 — Frontend Design Overhaul** | | | | | | See [tasks/phase3-frontend-design-overhaul.md](tasks/phase3-frontend-design-overhaul.md) |
| 30 | Design Foundation | Frontend | — | M | 30A, 30B | **Done** |
| 31 | App Shell Redesign | Frontend | 30 | M | — | **Done** |
| 32 | Landing Page | Frontend | 30 | L | 32A, 32B | **Done** |
| 33 | Core App Pages Redesign | Frontend | 30, 31 | L | 33A, 33B | **Done** |
| 34 | Supporting Pages Redesign | Frontend | 30, 31 | M | 34A, 34B | **Done** |
| 35 | Auth Pages & Dialog Restyling | Frontend | 30 | M | 35A, 35B | **Done** |
| 36 | Polish & Accessibility | Frontend | 30–35 | M | — | **Done** |
| **Phase 4 — Customers, Document Scopes & Tasks** | | | | | | See [tasks/phase4-customers-tasks-portal.md](tasks/phase4-customers-tasks-portal.md) |
| 37 | Customer Backend — Entity, CRUD & Linking | Backend | — | M | 37A, 37B | **Done** (PR #73, #74) |
| 38 | Customer Frontend — List, Detail & Dialogs | Frontend | 37 | M | 38A, 38B | **Done** (PR #75, #76) |
| 39 | Task Backend — Entity, CRUD, Claim & Release | Backend | — | M | 39A, 39B | **Done** (PR #77, #78) |
| 40 | Task Frontend — List, Creation & Claim UI | Frontend | 39 | M | 40A, 40B | **Done** (PR #79, #80) |
| 41 | Document Scope Extension — Backend | Backend | 37 | M | 41A, 41B | **Done** (PR #81, #82) |
| 42 | Document Scope Extension — Frontend | Frontend | 38, 41 | M | 42A, 42B | **Done** (PR #83, #84) |
| 43 | Customer Portal Groundwork | Both | 37, 41 | L | 43A, 43B, 43C | **Done** (PR #85, #86, #87) |
| **Phase 5 — Task & Time Lifecycle** | | | | | | See [tasks/phase5-task-time-lifecycle.md](tasks/phase5-task-time-lifecycle.md) |
| 44 | TimeEntry Backend — Entity, CRUD & Validation | Backend | — | M | 44A, 44B | **Done** (PR #88, #89) |
| 45 | TimeEntry Frontend — Log Time Dialog & Task Time List | Frontend | 44 | M | 45A, 45B | **Done** (PR #90, #91) |
| 46 | Project Time Summary — Backend | Backend | 44 | M | 46A | **Done** (PR #92) |
| 47 | Project Time Summary — Frontend | Frontend | 46 | S | 47A | **Done** (PR #94) |
| 48 | My Work — Backend | Backend | 44 | M | 48A | **Done** (PR #93) |
| 49 | My Work — Frontend | Frontend | 48 | M | 49A, 49B | **Done** (PR #95, #96) |
| **Phase 6 — Audit & Compliance Foundations** | | | | | | See [tasks/phase6-audit-compliance-foundations.md](tasks/phase6-audit-compliance-foundations.md) |
| 50 | Audit Infrastructure — Entity, Service & Migration | Backend | — | M | 50A, 50B | **Done** (PR #100, #101) |
| 51 | Domain Event Integration — Services | Backend | 50 | L | 51A, 51B | **Done** (PR #102, #103) |
| 52 | Security Event Integration | Backend | 50 | S | 52A | **Done** (PR #104) |
| 53 | Audit Query API | Backend | 50 | M | 53A, 53B | **Done** (PR #105, #106) |
| **Phase 6.5 — Notifications, Comments & Activity** | | | | | | See [tasks/phase6.5-notifications-comments-activity.md](tasks/phase6.5-notifications-comments-activity.md) |
| 59 | Comment Backend — Entity, Migration & CRUD API | Backend | — | M | 59A, 59B | **Done** (PR #107, #109) |
| 60 | Comment Frontend — CommentSection & Integration | Frontend | 59 | M | 60A, 60B | **Done** (PR #118, #119) |
| 61 | Domain Events & Notification Backend — Events, Entity, Migration & Handler | Backend | — | L | 61A, 61B, 61C | **Done** (PR #110, #111, #112) |
| 62 | Notification API & Preferences Backend | Backend | 61 | M | 62A, 62B | **Done** (PR #113, #114) |
| 63 | Notification Frontend — Bell, Page & Preferences UI | Frontend | 62 | M | 63A, 63B | **Done** (PR #120, #121) |
| 64 | Activity Feed Backend — Service, Formatter & API | Backend | 59 (V15) | M | 64A, 64B | **Done** (PR #115, #116) |
| 65 | Activity Feed Frontend — Activity Tab & Components | Frontend | 64 | S | 65A | **Done** (PR #122) |
| 66 | Email Notification Stubs — Channel Abstraction & Templates | Backend | 61 | S | 66A | **Done** (PR #117) |
| **Phase 7 — Customer Portal Backend Prototype** | | | | | | See [tasks/phase7-customer-portal-backend.md](tasks/phase7-customer-portal-backend.md) |
| 54 | PortalContact & Persistent Magic Links | Backend | -- | M | 54A, 54B | **Done** (PR #123, #124) |
| 55 | Portal Read-Model Schema & DataSource | Backend | 54 | M | 55A, 55B | **Done** (PR #125, #126) |
| 56 | Domain Events & Event Handlers | Backend | 55 | L | 56A, 56B, 56C | **Done** (PR #127, #128, #129) |
| 57 | Portal Comments, Summary & Profile APIs | Backend | 56 | M | 57A, 57B | **Done** (PR #130, #131) |
| 58 | Thymeleaf Dev Harness | Backend | 54, 57 | S | 58A | **Done** (PR #132) |
| **Phase 8 — Rate Cards, Budgets & Profitability** | | | | | | See [tasks/phase8-rate-cards-budgets-profitability.md](tasks/phase8-rate-cards-budgets-profitability.md) |
| 67 | OrgSettings & Rate Entity Foundation | Backend | -- | L | 67A, 67B, 67C, 67D | 67A **Done** (PR #133) |
| 68 | Rate Management Frontend — Settings, Project & Customer Rates | Frontend | 67 | M | 68A, 68B | |
| 69 | TimeEntry Rate Snapshots & Billable Enrichment | Backend | 67 | M | 69A, 69B | |
| 70 | TimeEntry Frontend — Billable UX & Rate Preview | Frontend | 69 | S | 70A | |
| 71 | Project Budgets — Entity, Status & Alerts | Backend | 69 | M | 71A, 71B | |
| 72 | Budget Frontend — Configuration & Status Visualization | Frontend | 71 | S | 72A | |
| 73 | Profitability Backend — Reports & Aggregation Queries | Backend | 69 | M | 73A, 73B | |
| 74 | Profitability & Financials Frontend — Pages & Tabs | Frontend | 73, 72, 68 | L | 74A, 74B, 74C | |

---

See [tasks/reference.md](tasks/reference.md) for the Epic Dependency Graph, Implementation Order, and Risk Register.
