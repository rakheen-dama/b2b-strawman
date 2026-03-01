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
| 67 | OrgSettings & Rate Entity Foundation | Backend | -- | L | 67A, 67B, 67C, 67D | **Done** (PRs #133–#136) |
| 68 | Rate Management Frontend — Settings, Project & Customer Rates | Frontend | 67 | M | 68A, 68B | **Done** (PRs #138, #139) |
| 69 | TimeEntry Rate Snapshots & Billable Enrichment | Backend | 67 | M | 69A, 69B | **Done** (PRs #137, #140) |
| 70 | TimeEntry Frontend — Billable UX & Rate Preview | Frontend | 69 | S | 70A | **Done** (PR #141) |
| 71 | Project Budgets — Entity, Status & Alerts | Backend | 69 | M | 71A, 71B | **Done** (PRs #143, #145) |
| 72 | Budget Frontend — Configuration & Status Visualization | Frontend | 71 | S | 72A | **Done** (PR #147) |
| 73 | Profitability Backend — Reports & Aggregation Queries | Backend | 69 | M | 73A, 73B | **Done** (PRs #144, #146) |
| 74 | Profitability & Financials Frontend — Pages & Tabs | Frontend | 73, 72, 68 | L | 74A, 74B, 74C | **Done** (PRs #148–#150) |
| **Phase 9 — Operational Dashboards** | | | | | | See [tasks/phase9-operational-dashboards.md](tasks/phase9-operational-dashboards.md) |
| 75 | Health Scoring & Project Health Endpoints | Backend | -- | M | 75A, 75B | **Done** |
| 76 | Company Dashboard Backend | Backend | 75 | L | 76A, 76B | **Done** |
| 77 | Shared Dashboard Components | Frontend | -- | M | 77A, 77B | **Done** |
| 78 | Company Dashboard Frontend | Frontend | 76, 77 | M | 78A, 78B | **Done** |
| 79 | Project Overview Tab | Both | 75, 77 | M | 79A, 79B | **Done** |
| 80 | Personal Dashboard | Both | 79A, 77 | M | 80A, 80B | **Done** |
| **Phase 10 — Invoicing & Billing from Time** | | | | | | See [tasks/phase10-invoicing-billing.md](tasks/phase10-invoicing-billing.md) |
| 81 | Invoice Entity Foundation & Migration | Backend | -- | M | 81A, 81B | **Done** (PRs #167, #168) |
| 82 | Invoice CRUD & Lifecycle Backend | Backend | 81 | L | 82A, 82B | **Done** (PRs #169, #170) |
| 83 | Unbilled Time & Invoice Generation | Both | 82 | L | 83A, 83B | **Done** (PRs #171, #173) |
| 84 | Invoice Detail & List Pages | Frontend | 83 | M | 84A | **Done** (PR #176) |
| 85 | Audit, Notification & HTML Preview | Both | 82 | L | 85A, 85B, 85C | **Done** (PRs #172, #174, #177) |
| 86 | Time Entry Billing UX | Both | 81A, 82 | M | 86A, 86B | **Done** (PRs #175, #178) |
| **Phase 11 — Tags, Custom Fields & Views** | | | | | | See [tasks/phase11-tags-custom-fields-views.md](tasks/phase11-tags-custom-fields-views.md) |
| 87 | Field Definition & Custom Field Backend | Backend | -- | L | 87A, 87B, 87C | **Done** (PRs #179, #181, #183) |
| 88 | Tags Backend | Backend | -- | M | 88A, 88B | **Done** (PRs #180, #185) |
| 89 | Saved Views Backend | Backend | 87C, 88B | M | 89A, 89B | **Done** (PRs #187, #189) |
| 90 | Field Pack Seeding | Backend | 87A | S | 90A | **Done** (PR #182) |
| 91 | Custom Fields Frontend | Frontend | 87, 90 | L | 91A, 91B | **Done** (PRs #184, #186) |
| 92 | Tags & Saved Views Frontend | Frontend | 88, 89 | L | 92A, 92B | **Done** (PRs #188, #190) |
| **Phase 12 — Document Templates & PDF Generation** | | | | | | See [tasks/phase12-document-templates.md](tasks/phase12-document-templates.md) |
| 93 | DocumentTemplate Entity Foundation | Backend | -- | L | 93A, 93B | **Done** (PRs #191, #192) |
| 94 | Rendering Pipeline & Generation | Backend | 93 | L | 94A, 94B | **Done** (PRs #193, #195) |
| 95 | Frontend — Template & Generation UI | Frontend | 93, 94 | L | 95A, 95B | **Done** (PRs #196, #197) |
| **Phase 13 — Dedicated Schema for All Tenants** | | | | | | See [tasks/phase13-dedicated-schema-only.md](tasks/phase13-dedicated-schema-only.md) |
| 96 | Infrastructure Removal & Provisioning Simplification | Backend | -- | M | 96A | **Done** |
| 97 | Entity, Repository & Call-Site Cleanup | Backend | 96 | L | 97A, 97B, 97C | **Done** |
| 98 | Migration Rewrite & Renumber | Backend | -- | M | 98A | **Done** |
| 99 | Test Cleanup, Verification & Documentation | Backend | 96, 97, 98 | M | 99A, 99B | **Done** |
| **Phase 14 — Customer Compliance & Lifecycle** | | | | | | See [tasks/phase14-customer-compliance-lifecycle.md](tasks/phase14-customer-compliance-lifecycle.md) |
| 100 | Customer Lifecycle Foundation | Backend | -- | M | 100A, 100B | **Done** (PRs #208, #209) |
| 101 | Checklist Template Engine | Backend | 100 | M | 101A, 101B | |
| 102 | Checklist Instance Engine | Backend | 101 | M | 102A, 102B | |
| 103 | Compliance Pack Seeding & Instantiation | Backend | 101 | M | 103A, 103B | **Done** (PRs #214, #215) |
| 104 | Data Subject Requests | Backend | 100 | M | 104A, 104B | 104A **Done** (PR #216) |
| 105 | Retention & Dormancy | Backend | 100 | M | 105A, 105B | 105A **Done** (PR #218) |
| 106 | Lifecycle & Checklist Frontend | Frontend | 102, 103 | M | 106A, 106B | **Done** (PRs #220, #221) |
| 107 | Data Requests & Settings Frontend | Frontend | 104, 105 | M | 107A, 107B | **Done** (PRs #222, #223) |
| 108 | Compliance Dashboard | Frontend | 106, 107 | S | 108A | **Done** (PR #224) |
| **Phase 15 — Contextual Actions & Setup Guidance** | | | | | | See [tasks/phase15-contextual-actions-setup-guidance.md](tasks/phase15-contextual-actions-setup-guidance.md) |
| 109 | Project Setup Status & Unbilled Time — Backend | Backend | -- | M | 109A, 109B | **Done** (PRs #225, #226) |
| 110 | Customer Readiness & Document Generation Readiness — Backend | Backend | -- | M | 110A, 110B | **Done** (PRs #227, #228) |
| 111 | Reusable Frontend Components & API Client | Frontend | 109, 110 | M | 111A | **Done** (PR #229) |
| 112 | Project Detail Page Integration | Frontend | 111 | S | 112A | **Done** (PR #230) |
| 113 | Customer Detail Page Integration | Frontend | 111 | S | 113A | **Done** (PR #231) |
| 114 | Empty State Rollout | Frontend | 111 | S | 114A | **Done** (PR #232) |
| **Phase 16 — Project Templates & Recurring Schedules** | | | | | | See [tasks/phase16-project-templates-recurring-schedules.md](tasks/phase16-project-templates-recurring-schedules.md) |
| 115 | Entity Foundation & Utilities | Backend | -- | M | 115A, 115B | **Done** (PRs #235, #236) |
| 116 | Template CRUD & Save from Project | Backend | 115 | M | 116A, 116B | **Done** (PRs #237, #238) |
| 117 | Template Instantiation | Backend | 116 | S | 117A | **Done** (PR #239) |
| 118 | Schedule CRUD & Lifecycle | Backend | 115 | M | 118A, 118B | **Done** (PRs #240, #241) |
| 119 | Scheduler Execution Engine | Backend | 117, 118 | M | 119A | **Done** (PR #242) |
| 120 | Template Management UI | Frontend | 116, 117 | M | 120A, 120B | **Done** (PRs #243, #244) |
| 121 | Schedule Management UI | Frontend | 118, 119 | M | 121A, 121B | **Done** (PRs #245, #246) |
| **Phase 17 — Retainer Agreements & Billing** | | | | | | See [tasks/phase17-retainer-agreements-billing.md](tasks/phase17-retainer-agreements-billing.md) |
| 122 | Entity Foundation & Migration | Backend | -- | M | 122A, 122B | **Done** (PRs #247, #248) |
| 123 | Retainer CRUD & Lifecycle | Backend | 122 | M | 123A, 123B | **Done** (PRs #249, #250) |
| 124 | Consumption Tracking & Summary | Backend | 123 | M | 124A, 124B | **Done** (PRs #251, #252) |
| 125 | Period Close & Invoice Generation | Backend | 124 | L | 125A, 125B | **Done** (PRs #253, #254) |
| 126 | Retainer Dashboard & Create UI | Frontend | 123, 124, 125 | M | 126A, 126B | **Done** (PRs #255, #256) |
| 127 | Customer Retainer Tab & Detail Page | Frontend | 126 | M | 127A, 127B | **Done** (PRs #257, #258) |
| 128 | Time Entry Indicators & Notifications | Both | 124, 127 | M | 128A, 128B | **Done** (PRs #259, #260) |
| **Phase 18 — Task Detail Experience** | | | | | | See [tasks/phase18-task-detail-experience-missing-functionality.md](tasks/phase18-task-detail-experience-missing-functionality.md) |
| 129 | Assignee Selection & Backend Prep | Both | -- | S | 129A | **Done** (PR #261) |
| 130 | Task Detail Sheet Core | Frontend | 129 | L | 130A, 130B | **Done** (PRs #262, #263) |
| 131 | Tags, Custom Fields & Saved Views | Frontend | 130 | M | 131A, 131B | **Done** (PRs #264, #265) |
| 132 | My Work Enhancements | Frontend | 131 | S | 132A | **Done** (PR #266) |
| **Phase 19 — Reporting & Data Export** | | | | | | See [tasks/phase19-reporting-data-export.md](tasks/phase19-reporting-data-export.md) |
| 133 | ReportDefinition Entity Foundation | Backend | — | M | 133A, 133B | **Done** (PRs #283, #284) |
| 134 | Report Execution Framework + Timesheet Query | Backend | 133 | M | 134A, 134B | **Done** (PRs #285, #286) |
| 135 | Invoice Aging + Project Profitability Queries | Backend | 134 | M | 135A | **Done** (PR #287) |
| 136 | Rendering & Export Pipeline | Backend | 134 | M | 136A, 136B | **Done** (PRs #288, #289) |
| 137 | Reports Frontend | Frontend | 133, 134 | M | 137A, 137B | **Done** (PRs #290, #291) |
| **Phase 20 — Auth Abstraction & E2E Testing Infrastructure** | | | | | | See [tasks/phase20-e2e-auth-abstraction.md](tasks/phase20-e2e-auth-abstraction.md) |
| 138 | Auth Abstraction Layer — Interface + Clerk Provider | Frontend | — | M | 138A, 138B | **Done** (PRs #292, #293) |
| 139 | 44-File Mechanical Refactor | Frontend | 138 | M | 139A, 139B | **Done** (PRs #294, #295) |
| 140 | Mock IDP Container + Backend E2E Profile | Infra | — | S | 140A | **Done** (PR #296) |
| 141 | Frontend Mock Provider — Server + Middleware | Frontend | 138 | M | 141A, 141B | **Done** (PRs #297, #298) |
| 142 | Frontend Mock Provider — Client Components | Frontend | 138 | S | 142A | **Done** (PR #299) |
| 143 | Docker Compose E2E Stack + Boot-Seed Container | Infra | 140, 141, 142 | M | 143A | **Done** (PR #300) |
| 144 | Playwright Fixtures + Smoke Tests | Frontend/E2E | 143 | S | 144A | **Done** (PR #301) |
| **Phase 21 — Integration Ports, BYOAK Infrastructure & Feature Flags** | | | | | | See [tasks/phase21-integration-ports-byoak.md](tasks/phase21-integration-ports-byoak.md) |
| 145 | StorageService Port + S3 Refactoring | Backend | — | M | 145A, 145B | **Done** (PRs #302, #303) |
| 146 | SecretStore Port + Encrypted Database Implementation | Backend | — | S | 146A | **Done** (PR #304) |
| 147 | Integration Port Interfaces + NoOp Stubs | Backend | — | M | 147A, 147B | **Done** (PRs #305, #306) |
| 148 | OrgIntegration Entity + IntegrationRegistry + BYOAK Infrastructure | Backend | 147 | M | 148A, 148B | **Done** (PRs #307, #308) |
| 149 | Feature Flags + IntegrationGuardService | Backend | — | S | 149A | **Done** (PR #309) |
| 150 | Integration Management API (Controller + Service) | Backend | 146, 148 | M | 150A, 150B | **Done** (PRs #310, #311) |
| 151 | Audit Integration for Config Events | Backend | 150 | S | 151A | **Done** (PR #312) |
| 152 | Integrations Settings UI | Frontend | 149, 150 | M | 152A, 152B | **Done** (PRs #313, #314) |
| **Phase 22 — Customer Portal Frontend** | | | | | | See [tasks/phase22-customer-portal-frontend.md](tasks/phase22-customer-portal-frontend.md) |
| 153 | Portal Read-Model Extension -- Invoice Sync + Endpoints | Backend | -- | M | 153A, 153B | **Done** (PRs #323, #324) |
| 154 | Portal Read-Model Extension -- Task Sync + Endpoint | Backend | 153A | S | 154A | **Done** (PR #325) |
| 155 | Portal Branding Endpoint + Comment POST | Backend | -- | S | 155A | **Done** (PR #326) |
| 156 | Portal App Scaffolding + Auth Flow | Portal | 155 | M | 156A, 156B | **Done** (PRs #327, #328) |
| 157 | Portal Shell, Branding + Project List Page | Portal | 156 | M | 157A | **Done** (PR #329) |
| 158 | Portal Project Detail Page | Portal | 157, 154, 155 | M | 158A | **Done** (PR #330) |
| 159 | Portal Invoice List + Detail Pages | Portal | 157, 153 | M | 159A | **Done** (PR #331) |
| 160 | Portal Profile, Responsive Polish + Docker | Portal | 156-159 | S | 160A | **Done** (PR #332) |
| **Phase 23 — Custom Field Maturity & Data Integrity** | | | | | | See [tasks/phase23-custom-field-maturity.md](tasks/phase23-custom-field-maturity.md) |
| 161 | Auto-Apply Field Groups & V38 Migration | Backend | -- | L | 161A, 161B | **Done** (PRs #333, #334) |
| 162 | Field Group Dependencies | Backend + Frontend | 161 | S | 162A | **Done** (PR #335) |
| 163 | Conditional Field Visibility | Backend + Frontend | 161 | M | 163A, 163B, 163C | **Done** (PRs #336, #337, #338) |
| 164 | Invoice Custom Fields & Task Pack | Backend + Frontend | 161 | M | 164A, 164B, 164C | **Done** (PRs #339, #340, #341) |
| 165 | Template Required Fields & Generation Validation | Backend + Frontend | 161, 164A | L | 165A, 165B, 165C | **Done** (PRs #342, #343, #344) |
| 166 | Rate Warnings & Bug Fixes | Backend + Frontend | -- | M | 166A, 166B | **Done** (PRs #345, #346) |
| **Phase 24 — Outbound Email Delivery** | | | | | | See [tasks/phase24-outbound-email-delivery.md](tasks/phase24-outbound-email-delivery.md) |
| 167 | EmailProvider Port + SMTP Adapter | Backend | -- | M | 167A, 167B | **Done** (PRs #348, #349) |
| 168 | Email Template Rendering | Backend | -- | M | 168A, 168B | **Done** (PRs #350, #351) |
| 169 | EmailNotificationChannel + Delivery Log + Migration | Backend | 167, 168 | L | 169A, 169B | **Done** (PRs #352, #353) |
| 170 | Invoice Delivery + Portal Magic Link Email | Backend | 169 | M | 170A, 170B | **Done** (PRs #354, #355) |
| 171 | SendGrid BYOAK + Bounce Webhooks | Backend | 169 | M | 171A, 171B | **Done** (PRs #356, #357) |
| 172 | Unsubscribe + Admin Endpoints | Backend | 169 | M | 172A, 172B | **Done** (PRs #358, #359) |
| 173 | Frontend — Email Toggle + Integration Card + Delivery Log | Frontend | 172 | M | 173A, 173B | **Done** (PRs #360, #361) |
| **Phase 25 — Online Payment Collection** | | | | | | See [tasks/phase25-online-payment-collection.md](tasks/phase25-online-payment-collection.md) |
| 174 | PaymentGateway Port + NoOp Adapter + InvoiceService Migration | Backend | -- | M | 174A, 174B | **Done** (PRs #362, #363) |
| 175 | PaymentEvent Entity + Migration + Invoice Extension | Backend | 174 | M | 175A, 175B | **Done** (PRs #364, #365) |
| 176 | Stripe Adapter | Backend | 174 | M | 176A, 176B | **Done** (PRs #366, #367) |
| 177 | PayFast Adapter | Backend | 174 | M | 177A, 177B | **Done** (PRs #368, #369) |
| 178 | Payment Link Generation + Webhook Reconciliation | Backend | 174, 175 | L | 178A, 178B | **Done** (PRs #370, #371) |
| 179 | Portal Payment Flow + Read-Model Extension | Both | 178 | M | 179A, 179B | **Done** (PRs #372, #373) |
| 180 | Integration Settings UI + Invoice Payment UX | Frontend | 178 | M | 180A, 180B | **Done** (PRs #374, #375) |
| **Phase 26 — Invoice Tax Handling** | | | | | | See [tasks/phase26-invoice-tax-handling.md](tasks/phase26-invoice-tax-handling.md) |
| 181 | TaxRate Entity Foundation + Migration | Backend | — | M | 181A, 181B | **Done** (PRs #376, #377) |
| 182 | Tax Calculation Engine + InvoiceLine Extension | Backend | 181 | M | 182A, 182B | **Done** (PRs #378, #379) |
| 183 | Tax Application in Invoice Flows | Backend | 182 | L | 183A, 183B | **Done** (PRs #380, #381) |
| 184 | Invoice Preview, PDF + Portal Tax Display | Backend + Portal | 183 | M | 184A, 184B | **Done** (PRs #382, #383) |
| 185 | Tax Settings + Rate Management Frontend | Frontend | 181 | M | 185A, 185B | **Done** (PRs #384, #385) |
| 186 | Invoice Editor Tax UI | Frontend | 183, 185 | M | 186A | **Done** (PR #386) |
| **Phase 27 — Document Clauses** | | | | | | See [tasks/phase27-document-clauses.md](tasks/phase27-document-clauses.md) |
| 187 | Clause Entity Foundation + Migration | Backend | -- | M | 187A, 187B | **Done** (PRs #387, #388) |
| 188 | Template-Clause Association API | Backend | 187 | M | 188A | **Done** (PR #389) |
| 189 | Generation Pipeline Extension | Backend | 187, 188 | L | 189A, 189B | **Done** (PRs #393, #394) |
| 190 | Clause Pack Seeder | Backend | 187, 188 | M | 190A | **Done** (PR #395) |
| 191 | Clause Library Frontend | Frontend | 187 | M | 191A, 191B | **Done** (PRs #390, #391) |
| 192 | Template Clauses Tab + Generation Dialog Frontend | Frontend | 188, 189, 191 | L | 192A, 192B | **Done** (PRs #392, #396) |
| **Phase 28 — Document Acceptance (Lightweight E-Signing)** | | | | | | See [tasks/phase28-document-acceptance.md](tasks/phase28-document-acceptance.md) |
| 193 | AcceptanceRequest Entity Foundation + Migration | Backend | -- | M | 193A, 193B | **Done** (PRs #397, #398) |
| 194 | AcceptanceService Core Workflow + Email | Backend | 193 | L | 194A, 194B | **Done** (PRs #399, #400) |
| 195 | Certificate Generation + Portal Read-Model Sync | Backend | 194 | M | 195A, 195B | **Done** (PRs #401, #402) |
| 196 | Firm-Facing REST API + Audit + Notifications | Backend | 194, 195 | M | 196A, 196B | **Done** (PRs #403, #404) |
| 197 | Portal Acceptance Controller + Expiry Processor | Backend | 194, 195 | M | 197A | **Done** (PR #405) |
| 198 | Frontend — Send for Acceptance + Status Tracking | Frontend | 196 | M | 198A, 198B | **Done** (PRs #406, #407) |
| 199 | Portal — Acceptance Page + Pending List | Portal | 197 | M | 199A, 199B | **Done** (PRs #408, #409) |
| 200 | Frontend — OrgSettings Acceptance Config | Frontend | 196 | S | 200A | **Done** (PR #410) |
| **Phase 29 — Entity Lifecycle & Relationship Integrity** | | | | | | See [tasks/phase29-entity-lifecycle-integrity.md](tasks/phase29-entity-lifecycle-integrity.md) |
| 201 | Task Lifecycle Foundation — Migration, Enums & Entity | Backend | -- | M | 201A, 201B | **Done** (PRs #411, #412) |
| 202 | Task Lifecycle Service + Transition Endpoints | Backend | 201 | L | 202A, 202B | **Done** (PRs #413, #415) |
| 203 | Project Lifecycle Foundation — Migration, Enums & Entity | Backend | -- | M | 203A, 203B | **Done** (PRs #416, #417) |
| 204 | Project Lifecycle Service + Transition Endpoints | Backend | 203, 201 | L | 204A, 204B | **Done** (PRs #418, #419) |
| 205 | Project-Customer Link + Due Date | Backend | 203 | M | 205A, 205B | **Done** (PRs #420, #421) |
| 206 | Delete Protection & Cross-Entity Guards | Backend | 201, 203, 205 | M | 206A, 206B | **Done** (PRs #422, #423) |
| 207 | Task Lifecycle Frontend | Frontend | 202 | M | 207A, 207B | **Done** (PRs #424, #425) |
| 208 | Project Lifecycle Frontend | Frontend | 204, 205 | L | 208A, 208B | **Done** (PRs #426, #427) |
| **Phase 31 — Document System Redesign: Rich Editor & Unified UX** | | | | | | See [tasks/phase31-document-system-redesign.md](tasks/phase31-document-system-redesign.md) |
| 209 | Database Migration & Pack Conversion | Backend | -- | L | 209A, 209B | **Done** (PRs #428, #429) |
| 210 | TiptapRenderer & Variable Endpoint | Backend | 209 | L | 210A, 210B | **Done** (PRs #430, #431) |
| 211 | Entity Updates & Template-Clause Sync | Backend | 209 | M | 211A, 211B | **Done** (PRs #432, #433) |
| 212 | Rendering Pipeline Switch & Legacy Import | Backend | 210, 211 | M | 212A, 212B | **Done** (PRs #434, #435) |
| 213 | Tiptap Editor Foundation | Frontend | -- | L | 213A, 213B, 213C | **Done** (PRs #436, #437, #438) |
| 214 | Template Editor Rewrite | Frontend | 210B, 211, 213 | L | 214A, 214B | **Done** (PRs #439, #440) |
| 215 | Clause Library & Editor Rewrite | Frontend | 213 | M | 215A, 215B | **Done** (PRs #441, #442) |
| 216 | Generation Dialog & Preview | Frontend | 213, 214 | M | 216A, 216B | **Done** (PRs #443, #444) |
| 217 | Backend Test Migration & Cleanup | Backend | 212 | M | 217A, 217B | **Done** (PRs #445, #446) |
| **Phase 30 — Expenses, Recurring Tasks & Daily Work Completeness** | | | | | | See [tasks/phase30-expenses-recurring-calendar.md](tasks/phase30-expenses-recurring-calendar.md) |
| 218 | Expense Entity Foundation & Migration | Backend | -- | M | 218A, 218B | **Done** (PRs #447, #448) |
| 219 | Expense Service, Controller & CRUD API | Backend | 218 | L | 219A, 219B | **Done** (PRs #449, #450) |
| 220 | Expense Frontend — Project Expenses Tab | Frontend | 219 | M | 220A, 220B | **Done** (PRs #451, #452) |
| 221 | Expense Billing Integration — InvoiceLine Extension & Invoice Pipeline | Backend | 218 | L | 221A, 221B | **Done** (PRs #453, #454) |
| 222 | Expense Billing Frontend — Unbilled Summary & Invoice Generation | Frontend | 221, 220 | M | 222A | **Done** (PR #455) |
| 223 | Recurring Task Foundation — Migration & Entity | Backend | -- | M | 223A, 223B | **Done** (PRs #456, #457) |
| 224 | Recurring Task Service & Controller | Backend | 223 | M | 224A, 224B | **Done** (PRs #458, #459) |
| 225 | Recurring Task Frontend | Frontend | 224 | M | 225A | **Done** (PR #460) |
| 226 | Time Reminder Scheduler & OrgSettings | Backend | -- | M | 226A, 226B | **Done** (PRs #461, #462) |
| 227 | Time Reminder Frontend — Settings & Preferences | Frontend | 226 | S | 227A | **Done** (PR #463) |
| 228 | Calendar View — Backend Endpoint | Backend | -- | M | 228A | **Done** (PR #464) |
| 229 | Calendar View — Frontend Page | Frontend | 228 | M | 229A, 229B | **Done** (PRs #465, #466) |
| **Phase 32 — Proposal → Engagement Pipeline** | | | | | | See [tasks/phase32-proposal-engagement-pipeline.md](tasks/phase32-proposal-engagement-pipeline.md) |
| 230 | Proposal Entity Foundation & Migration | Backend | -- | M | 230A, 230B | **Done** (PRs #467, #468) |
| 231 | Proposal CRUD & Lifecycle Backend | Backend | 230 | L | 231A, 231B | **Done** (PRs #469, #470) |
| 232 | Send Flow & Portal Read-Model Sync | Backend | 231 | M | 232A, 232B | **Done** (PRs #471, #472) |
| 233 | Acceptance Orchestration | Backend | 231 | L | 233A, 233B | **Done** (PRs #473, #474) |
| 234 | Portal Proposal Backend & Expiry Processor | Backend | 232, 233 | M | 234A, 234B | **Done** (PRs #475, #476) |
| 235 | Audit, Notifications & Activity Integration | Backend | 231 | S | 235A | **Done** (PR #477) |
| 236 | Proposals Frontend — List & Pipeline Stats | Frontend | 231 | M | 236A, 236B | **Done** (PRs #478, #479) |
| 237 | Proposals Frontend — Create/Edit & Detail Pages | Frontend | 232, 236 | L | 237A, 237B | **Done** (PRs #480, #481) |
| 238 | Proposals Frontend — Customer Tab & Project Link | Frontend | 236 | S | 238A | **Done** (PR #482) |
| 239 | Portal Frontend — Proposal Pages | Portal | 234 | M | 239A, 239B | **Done** (PRs #483, #484) |
| **Phase 33 — Data Completeness & Prerequisite Enforcement** | | | | | | See [tasks/phase33-data-completeness-prerequisites.md](tasks/phase33-data-completeness-prerequisites.md) |
| 240 | Prerequisite Infrastructure — Migration, Enum & Core Service | Backend | -- | M | 240A, 240B | **Done** (PRs #485, #486) |
| 241 | Prerequisite REST API & Field Definition Extension | Backend | 240 | M | 241A, 241B | **Done** (PRs #487, #489) |
| 242 | Lifecycle Transition Gate | Backend | 241 | M | 242A, 242B | **Done** (PRs #490, #491) |
| 243 | Engagement Prerequisites — Template Extension & Checks | Backend | 241 | M | 243A, 243B | **Done** (PRs #492, #493) |
| 244 | Action-Point Prerequisite Wiring | Backend | 241 | M | 244A, 244B | **Done** (PRs #494, #495) |
| 245 | PrerequisiteModal & Shared Frontend Components | Frontend | 241 | M | 245A, 245B | |
| 246 | Smart Customer Intake Dialog | Frontend | 241, 245 | M | 246A, 246B | |
| 247 | Prerequisite Configuration UI | Frontend | 245 | S | 247A | |
| 248 | Lifecycle Transition Frontend Integration | Frontend | 242, 245 | S | 248A | |
| 249 | Engagement & Action-Point Frontend Integration | Frontend | 243, 244, 245 | M | 249A, 249B | |
| 250 | Completeness Visibility — Backend Queries | Backend | 241 | M | 250A | |
| 251 | Completeness Visibility — Frontend & Dashboard | Frontend | 250, 245 | M | 251A, 251B | |
| **Phase 34 — Client Information Requests** | | | | | | See [tasks/phase34-client-information-requests.md](tasks/phase34-client-information-requests.md) |
| 252 | RequestTemplate Entity Foundation & Pack Seeder | Backend | -- | M | 252A, 252B | |
| 253 | InformationRequest Entity & Lifecycle Backend | Backend | 252 | L | 253A, 253B | |
| 254 | Domain Events, Portal Read-Model Sync & Portal API | Backend | 253 | L | 254A, 254B | |
| 255 | Notifications, Audit & Reminder Scheduler | Backend | 253, 254 | M | 255A, 255B | |
| 256 | Project Template Integration & OrgSettings Extension | Backend | 253, 255 | M | 256A | |
| 257 | Request Template Management UI | Frontend | 252 | M | 257A | |
| 258 | Firm-Side Request Pages & Review UI | Frontend | 253, 257 | L | 258A, 258B | |
| 259 | Portal Request Pages (Upload & Submit) | Frontend | 254 | M | 259A, 259B | |
| 260 | Dashboard Widget, Settings & Template Editor Integration | Frontend | 255, 256, 258 | M | 260A, 260B | |

---

See [tasks/reference.md](tasks/reference.md) for the Epic Dependency Graph, Implementation Order, and Risk Register.
