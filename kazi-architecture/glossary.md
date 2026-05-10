# Kazi Ubiquitous Language (Glossary)

Code-grounded, single source of vocabulary truth for Kazi (the product) across `backend/`, `frontend/`, `gateway/`, and `portal/`. Every term has a canonical name and one anchor in code (an entity, enum, REST path, or service). When product talk diverges from code, the table records the synonym to retire and the canonical replacement. This file is the lookup contributors and agents consult before writing a doc, an ADR, a UI string, or a spec.

**Last updated:** 2026-05-10
**Total terms:** 259
**Terms with code anchors:** 258
**Terms flagged as gap (no code anchor):** 1 (Workflow — translate to Automation Rule)

---

## How to use this glossary

- Look up any domain term to find its canonical name, code anchor (file:line, REST path, or package), and owning module.
- The "Notes / synonyms / watch-words" column flags retired terms — do not reintroduce them in code, ADRs, or UI copy.
- The Watch-words section (bottom of file) is a quick "heard this → say this" translator for ambiguous terms in conversation.
- Vertical overrides (e.g. legal calls Project a Matter, accounting calls it an Engagement) appear in the Notes column on each base term — the base term remains the canonical entry.
- Adding a new term: it must have a code anchor (entity, enum, type, or REST path). Never invent terms; if a term has no anchor, mark it `gap` and link it to the canonical replacement.
- If a term seems missing: check the Watch-words table first — it may be a synonym of an existing term.
- For module-to-context mapping, see `10-bounded-contexts.md`. For active architecture decisions, see `90-adr-index.md`.
- Cross-link convention: term names referenced in Notes are not yet hyperlinked — that is a future improvement; alphabetic sort makes Ctrl-F navigation reliable in the interim.

---

## Glossary

| Term | Sources | Code anchor | Module | Notes / synonyms / watch-words |
|---|---|---|---|---|
| Acceptance Request | backend entity, frontend type, REST path, ADR | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceRequest.java:22` | proposals-acceptance | Token-gated lightweight e-sign flow attaching a `GeneratedDocument` to a `PortalContact`. Status: `PENDING, SENT, VIEWED, ACCEPTED, EXPIRED, REVOKED`. Watch: not "Acceptance Test" (QA). |
| Acceptance Status | Java enum, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceStatus.java:4` | proposals-acceptance | `PENDING, SENT, VIEWED, ACCEPTED, EXPIRED, REVOKED`. Distinct from Proposal Status. |
| Access Request | backend entity, REST path `/api/access-requests`, `/api/platform-admin/access-requests` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequest.java:18` | platform-administration | Public OTP-verified sign-up application reviewed by Platform Admin. Retire synonyms: "Sign-up", "Registration", "Self-Service Onboarding" (Kazi is admin-gated only). |
| Access Request Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestStatus.java:3` | platform-administration | `PENDING_OTP, OTP_VERIFIED, APPROVED, REJECTED`. |
| Action Item | terminology override (legal-za) | `frontend/lib/terminology-map.ts:49` | terminology, legal-vertical | Legal-vertical UI label for a Task. Backed by `Task` entity. |
| Action Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ActionType.java:3` | automation-engine | `CREATE_TASK, SEND_NOTIFICATION, SEND_EMAIL, UPDATE_STATUS, CREATE_PROJECT, ASSIGN_MEMBER, INVOKE_AI_SPECIALIST`. |
| Activity Feed | REST path `/api/dashboard/activity`, portal route `/activity` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalActivityService.java:28` | notifications-activity, portal | Cross-entity event timeline. Portal tabs: `MINE, FIRM`. Watch: not the Audit Log (admin-only, immutable). |
| Adverse Party | backend entity, frontend type, REST path `/api/legal/adverse-parties` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdverseParty.java` | legal-vertical | Legal-only. Used in conflict-check workflow. Has `AdversePartyLink` join to matters. |
| Adverse Party Link | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/AdversePartyLink.java` | legal-vertical | Join table linking `AdverseParty` to `Project` (Matter). |
| AI Assistant | package, REST path `/api/assistant/chat`, frontend `AssistantPanel` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantController.java` | ai-assistant | In-app LLM chat with tool use, SSE-streamed. Watch: "Assistant" alone is ambiguous — always say "AI Assistant". |
| AI Invocation | frontend module, REST path | `frontend/lib/api/ai-invocations.ts` | automation-engine, ai-assistant | A call into the AI specialist registry, queued for review. See Invocation Status. |
| AI Queue | frontend route `/settings/automations/ai-queue` | `frontend/app/(app)/org/[slug]/settings/automations/ai-queue/page.tsx` | automation-engine, ai-assistant | Review queue for AI specialist invocations triggered by `INVOKE_AI_SPECIALIST` action. |
| AI Specialist | backend package `assistant/specialist` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/specialist/SpecialistRegistry.java` | ai-assistant | Named LLM context (system prompt + tool subset). Watch: not a person. |
| Allocation | backend entity, REST path `/api/allocations` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocation.java:16` | capacity-resource-planning | Member×project×week hours commitment. Distinct from Capacity (available hours). |
| Anonymization | REST path, frontend `AnonymizationResult`, ADR-data-protection | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/dataprotection/` | customer-lifecycle | Permanent PII scrub at customer lifecycle status `ANONYMIZED`. Irreversible. |
| API Key | header `X-API-KEY`, filter | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ApiKeyAuthFilter.java` | identity-access | Internal-only auth for `/internal/*` routes. Watch: not a Member API token (no such concept). |
| Audit Event | backend entity, REST path `/api/audit-events`, ADR-029, ADR-260 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java:29` | notifications-activity | Append-only, DB-trigger-immutable. Retire synonyms: "log entry", "activity record" (those refer to Activity Feed). |
| Audit Event Group | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventGroup.java:11` | notifications-activity | Categorisation for filtering audit log. |
| Audit Severity | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditSeverity.java:14` | notifications-activity | Severity tier for audit events. |
| Audit Trail | terminology override (legal-za) | `frontend/lib/terminology-map.ts:91` | terminology, legal-vertical | Legal-UI label for Audit Log. Same data. |
| Authentication Mode | env var `NEXT_PUBLIC_AUTH_MODE` | `frontend/lib/auth/server.ts` | identity-access | Either `keycloak` or `mock`. Drives provider switch. |
| Automation Action | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationAction.java:19` | automation-engine | Step in an automation rule: `actionType` + `actionConfig` (jsonb) + optional `delayDuration`. |
| Automation Execution | backend entity, REST path `/api/automation-rules/executions` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationExecution.java:19` | automation-engine | Single firing of a rule. Status enum `ExecutionStatus`. |
| Automation Rule | backend entity, REST path `/api/automation-rules`, frontend page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationRule.java:20` | automation-engine | Trigger + conditions + ordered actions. Watch: not "Workflow" (no such entity). |
| Automation Template | backend `RuleSource=PACK`, page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/RuleSource.java` | automation-engine, integrations-pack-system | Pack-shipped automation rule. Pack type `AUTOMATION_TEMPLATE`. |
| Bank Statement | frontend type | `frontend/lib/types/trust.ts` (BankStatementResponse) | legal-vertical | Imported file (CSV/OFX) for trust reconciliation matching. |
| Bank Statement Line | frontend type | `frontend/lib/types/trust.ts` (BankStatementLineResponse) | legal-vertical | Per-row match status `MATCHED, UNMATCHED, IGNORED`. |
| Billable | field on `TimeEntry`, `Expense` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java:17` | tasks-time, invoicing-billing | Boolean: "should appear on an invoice". Distinct from Billed (already invoiced) and the BillingStatus enums. |
| Billing Method | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/BillingMethod.java:7` | platform-administration | `PAYFAST, DEBIT_ORDER, PILOT, COMPLIMENTARY, MANUAL`. How the org pays Kazi — not how the org bills its customers. |
| Billing Rate | frontend type, REST path `/api/settings/rates` | `frontend/lib/types/billing.ts` (BillingRate) | invoicing-billing | Price per hour charged to customer. Distinct from Cost Rate. UI aliases: "Tariff Schedule" (legal), "Fee Schedule" (accounting), "Billing Rates" (consulting). Retire "Rate Card". |
| Billing Run | backend entity, REST path `/api/billing-runs`, frontend page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRun.java:18` | invoicing-billing | Bulk invoice generation across customers. Module-gated `bulk_billing`. Watch: not a Billing Cycle — Kazi has no end-customer subscription cycles. |
| Billing Run Item Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunItemStatus.java` | invoicing-billing | Per-customer item state inside a run. |
| Billing Run Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunStatus.java` | invoicing-billing | `DRAFT, GENERATING, READY, APPROVED, SENDING, SENT, CANCELLED`. |
| Billing Status (TimeEntry) | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/BillingStatus.java:13` | tasks-time, invoicing-billing | `UNBILLED, BILLED, NON_BILLABLE`. Watch: three "BillingStatus" enums exist (TimeEntry, Expense, Disbursement) — always disambiguate. |
| Brand Color | field `OrgSettings.brandColor`, CSS var `--brand-color` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` | settings, identity-access | Per-tenant accent injected into sidebar. |
| Branding | REST path `/portal/branding`, `/api/settings` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalBrandingController.java` | settings, portal | Customer-facing logo + colour + name. Surfaced pre-auth on portal login. |
| Budget | frontend type `BudgetStatusResponse`, backend `budget` package | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetStatus.java:28` | invoicing-billing | Project-level fee cap. Legal label: "Fee Estimate". Triggers `BudgetThresholdEvent`. |
| Budget Status | Java enum `BudgetStatusEnum` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetStatus.java:28` | invoicing-billing | `OK, NEAR_LIMIT, OVER`. |
| Capability | Java enum, REST path `/api/me/capabilities` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7` | identity-access | Atomic permission. Includes `FINANCIAL_VISIBILITY, INVOICING, PROJECT_MANAGEMENT, TEAM_OVERSIGHT, CUSTOMER_MANAGEMENT, AUTOMATIONS, RESOURCE_PLANNING, MANAGE_COMPLIANCE, MANAGE_COMPLIANCE_DESTRUCTIVE, VIEW_LEGAL, MANAGE_LEGAL, VIEW_TRUST, MANAGE_TRUST, APPROVE_TRUST_PAYMENT, MANAGE_DISBURSEMENTS, APPROVE_DISBURSEMENTS, WRITE_OFF_DISBURSEMENTS, CLOSE_MATTER, OVERRIDE_MATTER_CLOSURE, GENERATE_STATEMENT_OF_ACCOUNT`. Watch: not "Permission" (use Capability), not "Role" (Role is a bundle). |
| Capacity | backend entity `MemberCapacity`, REST path `/api/capacity` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberCapacity.java:16` | capacity-resource-planning | Member's available hours per week. |
| Cashbook Balance | frontend type | `frontend/lib/types/trust.ts` (CashbookBalance) | legal-vertical | Trust ledger book balance. Part of reconciliation. |
| Checklist Instance | backend entity, REST path `/api/checklist-instances` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistInstance.java:14` | customer-lifecycle | Per-customer running instance of a `ChecklistTemplate`. |
| Checklist Template | backend entity, REST path `/api/checklist-templates`, frontend page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/checklist/ChecklistTemplate.java:16` | customer-lifecycle | Pack-installable compliance checklist. |
| Clause | backend entity `DocumentClause`, frontend page `/settings/clauses` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/` | documents-templates | Reusable doc fragment included in a `DocumentTemplate`. |
| Clause Source | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/clause/ClauseSource.java:4` | documents-templates | `SYSTEM, PACK, CUSTOM`. |
| Clerk User Id | field `Member.clerkUserId` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java:22` | identity-access | Legacy field name from Clerk era — Clerk is fully removed (now Keycloak). Field still named `clerkUserId` but holds Keycloak `sub`. Divergence #6 below. |
| Client | terminology override (all verticals) | `frontend/lib/terminology-map.ts:3` | terminology | UI label for `Customer` in legal-za, accounting-za, consulting-za. Code always says Customer. |
| Client Ledger | frontend type, REST path `/portal/trust/...` | `frontend/lib/types/trust.ts` (ClientLedgerCard) | legal-vertical | Per-client trust money ledger. Statutory legal-vertical concept. |
| Client Ledger Card | frontend type | `frontend/lib/types/trust.ts` | legal-vertical | UI card view of a client's trust balance. |
| Closure Gate | backend interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/ClosureGate.java` | legal-vertical | Compliance precondition for closing a Matter. Composed via `MatterClosureService`. |
| Closure Reason | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/dto/ClosureReason.java:7` | legal-vertical | Why a matter is being closed. |
| Comment | backend entity, REST path | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java:15` | notifications-activity | Threaded note on Project/Task/Customer. Has `visibility` (INTERNAL/PORTAL). |
| Compliance Pack | backend, frontend, ADR | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CompliancePackSeeder.java` | customer-lifecycle, integrations-pack-system | Pack of `ChecklistTemplate`s + retention rules per jurisdiction. |
| Completeness Score | frontend type, backend service | `frontend/lib/types/common.ts` (CompletenessScore) | customer-lifecycle | Percent of required custom fields filled. Drives prerequisite gates. |
| Concrete Studio | design-system palette name | `frontend/CLAUDE.md` | (frontend design tokens) | Light-mode token palette. Not user-facing language. |
| Condition Operator | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/ConditionOperator.java:3` | automation-engine | EQ/NE/GT/LT/etc. for automation rule conditions. |
| Conflict Check | backend entity, frontend type, REST path `/api/legal/conflict-checks` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/conflictcheck/ConflictCheck.java` | legal-vertical | Conflict-of-interest scan for new matter intake. |
| Conflict Check Result | frontend type | `frontend/lib/types/legal.ts` (ConflictCheckResult) | legal-vertical | `NO_CONFLICT, CONFLICT_FOUND, POTENTIAL_CONFLICT`. |
| Conflict Check Type | frontend type | `frontend/lib/types/legal.ts` | legal-vertical | `NEW_CLIENT, NEW_MATTER, PERIODIC_REVIEW`. |
| Conflict Match | frontend type | `frontend/lib/types/legal.ts` (ConflictMatch) | legal-vertical | A hit row in a `ConflictCheck`. |
| Conflict Resolution | frontend type | `frontend/lib/types/legal.ts` | legal-vertical | `PROCEED, DECLINED, WAIVER_OBTAINED, REFERRED`. |
| Cost Rate | frontend type | `frontend/lib/types/billing.ts` (CostRate) | invoicing-billing | Internal cost per member-hour for profitability. Distinct from Billing Rate. Never visible to customer. |
| Court Date | backend entity, frontend type, REST path `/api/legal/court-dates` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDate.java` | legal-vertical | Module `court_calendar`. |
| Court Date Status | frontend type | `frontend/lib/types/legal.ts` (CourtDateStatus) | legal-vertical | `SCHEDULED, POSTPONED, HEARD, CANCELLED`. |
| Court Date Type | frontend type | `frontend/lib/types/legal.ts` (CourtDateType) | legal-vertical | Hearing, Trial, Pre-Trial, etc. |
| Court Level | field `TariffSchedule.courtLevel` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffSchedule.java:19` | legal-vertical | Magistrates / High Court / Constitutional. |
| CSRF Token | gateway header `X-XSRF-TOKEN`, cookie `XSRF-TOKEN` | `gateway/src/main/java/io/b2mash/b2b/gateway/config/SpaCsrfTokenRequestHandler.java` | gateway | BREACH-mitigated SPA token. Disabled for `/api/**` and `/bff/**`. |
| Custom Field | backend entity `FieldDefinition`, REST path `/api/field-definitions` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java:22` | custom-fields-views | Tenant-defined attribute with `EntityType` + `FieldType`. |
| Customer | backend entity, frontend type, REST path `/api/customers` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java:23` | customer-lifecycle | The end-client of the tenant org. UI label: "Client" (all 3 vertical profiles). Retire: "Account" (CRM term), "Lead" (no such status — `PROSPECT` is the lifecycle stage), "Contact" (means PortalContact). |
| Customer Project | backend entity (join table) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProject.java:14` | projects | Many-to-many link Customer↔Project. |
| Customer Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerType.java:4` | customer-lifecycle | `INDIVIDUAL, COMPANY, TRUST`. Watch: TRUST here means a legal/tax trust entity — NOT trust accounting (divergence #4). |
| Data Subject Request | backend service `DataSubjectRequestService`, frontend type `DsarRequest` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequestService.java` | customer-lifecycle | POPIA/GDPR access/deletion request. Synonym: DSAR. |
| Deadline | frontend type, REST path `/api/legal/deadlines`, `/portal/deadlines` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/...` (deadline service) | legal-vertical | Module `regulatory_deadlines`. Distinct from Project Due Date and Court Date. |
| Default Currency | field `OrgSettings.defaultCurrency` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` | settings, invoicing-billing | ISO-4217 code. |
| Delay Unit | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/DelayUnit.java:3` | automation-engine | `MINUTES, HOURS, DAYS`. |
| Disbursement | backend entity (legal), invoice line type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/` | legal-vertical, invoicing-billing | Out-of-pocket expense passed through to client. Legal-vertical UI replaces "Expense" entirely. Invoice line type `DISBURSEMENT`. |
| Disbursement Approval Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementApprovalStatus.java:9` | legal-vertical | Approval state for a recorded disbursement. |
| Disbursement Billing Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementBillingStatus.java:9` | legal-vertical, invoicing-billing | Whether disbursement has been billed. One of three "BillingStatus" enums. |
| Disbursement Category | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementCategory.java:11` | legal-vertical | `SHERIFF_FEES, COUNSEL_FEES, SEARCH_FEES, DEEDS_OFFICE_FEES, COURT_FEES, ADVOCATE_FEES, EXPERT_WITNESS, TRAVEL, OTHER`. |
| Disbursement Payment Source | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementPaymentSource.java:14` | legal-vertical | Where disbursement was paid from (firm, trust, advanced). |
| Document | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java:16` | documents-templates | Uploaded file with `scope` (PROJECT/CUSTOMER) and `visibility`. Distinct from `GeneratedDocument`. |
| Document Acceptance | flow name | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceRequest.java:22` | proposals-acceptance | The end-to-end e-sign workflow. UI label. |
| Document Generation | service, event `DocumentGeneratedEvent` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentGenerationService.java` | documents-templates | Render `DocumentTemplate` (Tiptap JSON + Mustache) → `GeneratedDocument`. |
| Document Status | Java enum (inner) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/document/Document.java:175` | documents-templates | `UPLOADED, SCANNING, READY, etc.` (S3 lifecycle). |
| Document Template | backend entity, frontend page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java:22` | documents-templates | Tiptap-JSON-content document blueprint. Pack-installable. |
| Domain Event | sealed Java interface, ADR-029 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java:17` | notifications-activity (cross-cutting) | Spring `ApplicationEventPublisher` backbone. ~35 record implementations. |
| Dormancy | scheduler `DormancyScheduler`, lifecycle status `DORMANT` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyScheduler.java` | customer-lifecycle | Customer auto-transitions to DORMANT when idle longer than `OrgSettings.dormancyThresholdDays`. |
| DSAR | abbreviation, frontend type `DsarRequest` | `frontend/lib/types/data-protection.ts` | customer-lifecycle | Data Subject Access Request. Synonym for Data Subject Request. |
| Email Delivery Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/email/EmailDeliveryStatus.java:4` | integrations-pack-system | `QUEUED, SENT, FAILED, BOUNCED`. |
| Email Template | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/template/EmailTemplate.java:10` | notifications-activity | Stable identifiers for templated emails. |
| Engagement | terminology override (accounting-za) | `frontend/lib/terminology-map.ts:21` | terminology, accounting-vertical | Accounting-vertical UI label for `Project`. Backend always says Project. |
| Engagement Letter | terminology override (accounting-za, legal-za) | `frontend/lib/terminology-map.ts:29` | terminology | UI label for `Proposal` in accounting + legal. |
| Entity Tag | backend entity (join) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/EntityTag.java:14` | custom-fields-views | Polymorphic tag↔entity link. |
| Entity Type | Java enum, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/EntityType.java:4` | custom-fields-views | Polymorphic discriminator: `PROJECT, TASK, CUSTOMER, INVOICE, etc.` |
| Expense | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/Expense.java:19` | tasks-time, invoicing-billing | Reimbursable cost. Legal-UI label: "Disbursement". `ExpenseCategory` is generic; legal uses `DisbursementCategory` instead. |
| Expense Billing Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseBillingStatus.java:4` | invoicing-billing | One of three "BillingStatus" enums — disambiguate. |
| Expense Category | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseCategory.java:4` | tasks-time | Generic categories. Legal vertical uses `DisbursementCategory` instead. |
| External Org Id | field `Organization.externalOrgId` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java` | tenancy-provisioning | Identifier from external IDP (Keycloak `o.id`). Maps to schema via `OrgSchemaMapping`. |
| Fee Estimate | terminology override (legal-za) | `frontend/lib/terminology-map.ts:75` | terminology, legal-vertical | UI label for `Budget`. |
| Fee Model | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/FeeModel.java:4` | proposals-acceptance | `FIXED, HOURLY, RETAINER, CONTINGENCY`. Per-proposal pricing model. |
| Fee Note | terminology override (legal-za) | `frontend/lib/terminology-map.ts:67` | terminology, legal-vertical | UI label for `Invoice`. |
| Fee Schedule | terminology override (accounting-za) | `frontend/lib/terminology-map.ts:33` | terminology, accounting-vertical | UI label for Billing Rate / Rate Card. |
| FICA | frontend types `lib/types/fica.ts`, REST path `/api/customers/{id}/fica-status` | `frontend/lib/types/fica.ts` | legal-vertical, customer-lifecycle | South African KYC/AML regulation. Customer compliance check. |
| Field Date Approaching | event class | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/fielddate/FieldDateApproachingEvent.java` | automation-engine | Fires when a custom DATE field is N days from now. |
| Field Definition | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldDefinition.java:22` | custom-fields-views | Single custom field schema row. |
| Field Group | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldGroup.java:19` | custom-fields-views | Grouping of `FieldDefinition`s; supports `autoApply` to entities. |
| Field Pack | concept (`OrgSettings.fieldPackStatus`), pack type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstaller.java` | integrations-pack-system | Pack containing FieldDefinitions + FieldGroups. |
| Field Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/fielddefinition/FieldType.java:4` | custom-fields-views | `TEXT, NUMBER, DATE, DROPDOWN, BOOLEAN, CURRENCY, URL, EMAIL, PHONE`. |
| Generated Document | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/GeneratedDocument.java:22` | documents-templates | Output of rendering a `DocumentTemplate`. Stored to S3, optionally promoted to a `Document`. |
| Grace Period | subscription status, frontend `GraceCountdown` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java:277` | platform-administration | Post-failed-payment window before SUSPENDED. |
| Health Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/HealthStatus.java:4` | dashboard | Project health scoring tier. |
| Hour Bank | portal UI concept (Retainer) | `portal/components/retainer/HourBankCard` | invoicing-billing, portal | Visual representation of remaining `RetainerPeriod.allocatedHours - consumedHours`. |
| Idempotent Push | concept, ADR-278 | `adr/ADR-278-idempotent-push-via-external-reference.md` | integrations-pack-system | Xero accounting sync uses external reference for idempotency. |
| Information Request | backend entity, REST path `/api/information-requests`, portal page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequest.java:22` | information-requests | Structured client data-collection workflow. Distinct from Data Subject Request. |
| Information Request Status | Java enum `RequestStatus` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestStatus.java:4` | information-requests | `DRAFT, SENT, IN_PROGRESS, COMPLETED, CANCELLED`. |
| Integration Domain | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java:4` | integrations-pack-system | `ACCOUNTING, AI, DOCUMENT_SIGNING, EMAIL, KYC_VERIFICATION, PAYMENT`. |
| Integration Guard | service | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java` | integrations-pack-system | Enforces tenant has integration enabled before adapter call. |
| Interest Run | backend entity (LPFF), frontend page `/trust-accounting/interest` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/` | legal-vertical | Periodic LPFF interest calculation across client ledgers. |
| Interest Run Status | frontend type | `frontend/lib/types/trust.ts` (InterestRunStatus) | legal-vertical | `DRAFT, APPROVED, POSTED`. |
| Investment Basis | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/InvestmentBasis.java` | legal-vertical | `FIRM_DISCRETION, CLIENT_INSTRUCTION` for trust investments. |
| Invitation | backend entity `PendingInvitation`, REST path | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitation.java:22` | identity-access | App-managed role invite to join the org. Distinct from Keycloak invitations. |
| Invitation Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/InvitationStatus.java:3` | identity-access | `PENDING, ACCEPTED, EXPIRED, REVOKED`. |
| Invocation Source | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/InvocationSource.java:4` | ai-assistant, automation-engine | Source of an AI specialist invocation (chat, automation, etc.). |
| Invocation Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/InvocationStatus.java:4` | ai-assistant | AI specialist queue status. |
| Invoice | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java:24` | invoicing-billing | Bill to a Customer. Legal-UI label: "Fee Note". |
| Invoice Line | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java:19` | invoicing-billing | Single line on an invoice. |
| Invoice Line Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineType.java:4` | invoicing-billing | `TIME, EXPENSE, RETAINER, MANUAL, FIXED_FEE, TARIFF, DISBURSEMENT`. |
| Invoice Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceStatus.java:3` | invoicing-billing | `DRAFT, APPROVED, SENT, PAID, VOID`. |
| Item Status | Java enum (information-request) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/ItemStatus.java:4` | information-requests | Per-item submission state. |
| KPI | dashboard widget, REST path `/api/dashboard/kpis` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/DashboardController.java` | dashboard | Org-level metric tile. |
| KYC | integration domain, frontend type | `frontend/lib/types/kyc.ts` | integrations-pack-system, customer-lifecycle | Identity verification adapter. In legal-za context implies FICA. |
| KYC Verification Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/kyc/KycVerificationStatus.java:4` | integrations-pack-system | Provider-agnostic status. |
| Ledger Statement | frontend type, REST path | `frontend/lib/types/trust.ts` (LedgerStatementResponse) | legal-vertical | Per-matter trust statement document. |
| Lifecycle Action | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/LifecycleAction.java:3` | customer-lifecycle | Discrete user/system actions that move customers between lifecycle statuses (ACTIVATE, OFFBOARD, ANONYMIZE, etc.). |
| Lifecycle Status | Java enum, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java:7` | customer-lifecycle | `PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED, ANONYMIZED`. State machine enforced. `PROSPECT` is the default for new customers (not "Lead"). |
| LPFF | acronym, package `verticals/legal/trustaccounting/lpff` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/` | legal-vertical | Legal Practitioners' Fidelity Fund. SA statutory body that receives interest from general trust accounts. |
| LSSA Tariff | module `lssa_tariff`, frontend page `/legal/tariffs` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/` | legal-vertical | Law Society of South Africa tariff schedules. See Tariff Schedule. |
| Magic Link | concept, REST path `/portal/auth/request-link`, entity `MagicLinkToken` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkToken.java:19` | portal | One-time email-delivered token for portal sign-in. Also used for token-gated `Acceptance Request` and `Proposal` flows. |
| Mandate | terminology override (legal-za) | `frontend/lib/terminology-map.ts:79` | terminology, legal-vertical | UI label for `Retainer`. |
| Matter | terminology override (legal-za) | `frontend/lib/terminology-map.ts:45` | terminology, legal-vertical | UI label for `Project` in legal vertical. Backed by `Project` entity. Backend never uses "Matter" except in `MatterClosure*` legal closure services. |
| Matter Closure | service, controller, log entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java` | legal-vertical | Compliance-gated transition from `ACTIVE` → `CLOSED` for legal matters. |
| Matter Closure Log | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureLog.java` | legal-vertical | Append-only record of a closure attempt with gate outcomes. |
| Member | backend entity, REST path `/api/members` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java:22` | identity-access | A user inside a tenant org. Linked by `clerkUserId` to the IDP. Distinct from `PortalContact` (external customer-side user) and `ProjectMember` (project access grant). |
| Member Capacity | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/MemberCapacity.java:16` | capacity-resource-planning | Weekly available hours for a member. |
| Mock Auth | env mode, route group `(mock-auth)` | `frontend/app/(mock-auth)/mock-login/page.tsx` | identity-access | E2E-only authentication. Production never enables. |
| Module | concept, settings flag, JSON `enabledModules` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/ModuleCategory.java:8` | vertical-profile-system | Toggleable feature unit. Distinct from Capability (capability=permission, module=feature). Module slugs: `court_calendar, conflict_check, lssa_tariff, trust_accounting, regulatory_deadlines, bulk_billing, resource_planning, automation_builder, retainer_agreements, information_requests, deadlines`. |
| Module Category | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/ModuleCategory.java:8` | vertical-profile-system | `VERTICAL` (auto-bound to profile) or `HORIZONTAL` (admin-toggleable). |
| Module Gate | frontend component | `frontend/components/module-gate.tsx:11` | vertical-profile-system | UI gate hiding a section when the module is off. |
| My Work | frontend route `/my-work`, types `MyWorkTaskItem` | `frontend/lib/types/project.ts:95` | tasks-time | Personal view of assigned tasks + recent time entries. |
| Notification | backend entity, REST path `/api/notifications` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/Notification.java:13` | notifications-activity | In-app message to a member. |
| Notification Preference | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationPreference.java:13` | notifications-activity | Per-member, per-type opt-in flags (in-app + email). |
| OAuth2 Login | gateway concept | `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java` | gateway, identity-access | Keycloak OIDC code flow handled by gateway. |
| Offboarding | lifecycle status, REST path `/api/customers/{id}/transition` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java:7` | customer-lifecycle | Customer winding-down state. Followed by `OFFBOARDED`, then optional `ANONYMIZED`. |
| Onboarding | lifecycle status | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java:7` | customer-lifecycle | The active intake phase before `ACTIVE`. Not the platform sign-up (that's Access Request). |
| Org | abbreviation for Organization (URL slug `/org/[slug]`) | `frontend/app/(app)/org/[slug]/layout.tsx` | tenancy-provisioning | URL/UI shorthand for Organization. Retire "Tenant" in product talk (use Org), retire "Workspace" entirely. |
| Org Integration | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java:20` | integrations-pack-system | Per-org adapter config. |
| Org Member | REST path `/api/members`, controller `OrgMemberController` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/OrgMemberController.java` | identity-access | Same as Member; "Org Member" is the contextualised term contrasted with `ProjectMember`. |
| Org Role | backend entity, REST path `/api/org-roles` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRole.java:23` | identity-access | A bundle of capabilities. System and custom roles. Distinct from Project Role. |
| Org Schema Mapping | backend entity (public schema) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java:14` | tenancy-provisioning | Maps `externalOrgId` → tenant schema name. |
| Org Settings | backend entity, REST path `/api/settings` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:34` | settings | Single-row-per-tenant config aggregate (~25 fields). |
| Organization | backend entity (public schema) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java` | tenancy-provisioning | The tenant. Short form is "Org". Avoid "Company" (legacy term — `Customer.customerType=COMPANY` is unrelated). |
| Output Format | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/OutputFormat.java:3` | documents-templates | Template render format (PDF/DOCX/HTML). |
| Pack | backend, REST path `/api/packs` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/` | integrations-pack-system | Bundle of seedable content. Five pack types. Not a software dependency package. |
| Pack Install | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstall.java:17` | integrations-pack-system | Record of an installed pack version. |
| Pack Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackType.java:7` | integrations-pack-system | `DOCUMENT_TEMPLATE, AUTOMATION_TEMPLATE` (and historically: field, compliance, clause). |
| PAIA | acronym, frontend `PaiaGenerateResponse` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/dataprotection/` | legal-vertical, customer-lifecycle | Promotion of Access to Information Act (SA). Manual generation feature. |
| Payment Event | backend, REST path `/api/invoices/payment-events` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentEventStatus.java:4` | invoicing-billing | Recorded receipt against an invoice (incl. partial reversals). |
| Payment Event Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentEventStatus.java:4` | invoicing-billing | `RECORDED, REVERSED, PARTIALLY_REVERSED`. |
| Payment Status (integration) | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/PaymentStatus.java:7` | integrations-pack-system | Adapter-level payment outcome. One of three "PaymentStatus"-named enums. |
| Payment Status (subscription) | Java enum (inner) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionPayment.java:113` | platform-administration | Platform billing payment status. |
| PayFast | adapter (subscription billing) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/payfast/PlatformPayFastService.java` | platform-administration | South African payment processor for subscription payments. |
| Pending Invitation | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitation.java:22` | identity-access | App-managed pre-acceptance invitation row. |
| Period Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/PeriodStatus.java:3` | invoicing-billing | Retainer period state: `OPEN, CLOSED, INVOICED`. |
| Platform Admin | role/group, REST path prefix `/api/platform-admin/*`, filter | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/PlatformAdminFilter.java` | platform-administration | Cross-tenant Kazi staff. Identified by JWT `groups` claim. Distinct from Org Owner role. |
| Portal | app `portal/`, package `backend/.../portal` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/` | portal | Customer-facing Next.js app. Do not call it "Client Portal" in code (UI label OK). |
| Portal Branding | REST path, frontend hook `use-branding` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalBrandingController.java` | portal, settings | Pre-auth fetched org branding for portal login screen. |
| Portal Contact | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContact.java:16` | portal | A person on the customer side who can use the portal. Roles `PRIMARY, BILLING, GENERAL`; statuses `ACTIVE, SUSPENDED, ARCHIVED`. Divergence #1: UI sometimes says "Customer Contact" — backend always says PortalContact. |
| Portal Digest Cadence | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/PortalDigestCadence.java:8` | portal, notifications-activity | How often portal contacts get email digests. |
| Portal JWT | concept, `localStorage` key `portal_jwt` | `portal/lib/auth.ts` | portal, identity-access | Custom JWT, NOT an OAuth2 token. Stored client-side. Completely separate from staff-frontend Keycloak session. |
| Portal Retainer Member Display | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/PortalRetainerMemberDisplay.java:8` | portal, settings | Whether portal shows member names on retainer consumption. |
| Portal Session Context | REST path `/portal/session/context`, frontend hook | `portal/hooks/use-portal-context.ts` | portal | Bundle of `tenantProfile, enabledModules, terminologyKey, brandColor, orgName, logoUrl` returned per portal session. |
| Prerequisite | service, REST path `/api/customers/{id}/setup-status` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteContext.java:4` | customer-lifecycle | Required-fields gate before lifecycle transitions or document generation. |
| Prerequisite Context | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteContext.java:4` | customer-lifecycle | Where the prerequisite check is being evaluated. |
| Prescription Tracker | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/PrescriptionTracker.java` | legal-vertical | Statute-of-limitations countdown. Status `RUNNING, WARNED, INTERRUPTED, EXPIRED`. |
| Prescription Type | frontend type | `frontend/lib/types/legal.ts` (PrescriptionType) | legal-vertical | Statutory limitation category. |
| Processing Activity | backend, frontend type | `frontend/lib/types/data-protection.ts` (ProcessingActivity) | customer-lifecycle | POPIA register entry. |
| Project | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:24` | projects | Core deliverable unit. UI labels: "Matter" (legal-za), "Engagement" (accounting-za). Backend never says Matter except in legal closure subpackage. |
| Project Health | dashboard widget, REST path | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dashboard/HealthStatus.java:4` | dashboard, projects | RAG-style indicator per project. |
| Project Member | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMember.java:14` | projects, identity-access | Per-project access grant. Has `projectRole`. Distinct from Org Member — a Member can be on the org but not a project. |
| Project Priority | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectPriority.java:4` | projects | Priority tier. |
| Project Role | frontend type, field `ProjectMember.projectRole` | `frontend/lib/types/member.ts:3` | projects | Project-scoped role (lead/contributor/observer). Distinct from Org Role. |
| Project Status | Java enum, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectStatus.java:7` | projects | `ACTIVE, COMPLETED, ARCHIVED, CLOSED`. `CLOSED` is legal-vertical only (compliance-gated, retention anchor) and is non-terminal — supports `reopen` (divergence #5). |
| Project Template | backend entity, REST path `/api/project-templates` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/ProjectTemplate.java:18` | projects, integrations-pack-system | Blueprint that instantiates a Project + tasks. Schedule entity drives recurrence. |
| Proposal | backend entity, REST path `/api/proposals`, frontend page | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java:31` | proposals-acceptance | Pre-engagement quote/letter sent to customer. UI label: "Engagement Letter" (legal, accounting). Status enum `ProposalStatus` distinct from `AcceptanceStatus`. |
| Proposal Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalStatus.java:4` | proposals-acceptance | `DRAFT, SENT, ACCEPTED, DECLINED, EXPIRED`. |
| Provisioning Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java:95` | tenancy-provisioning | Org Flyway/seed completion status. |
| Reconciliation | trust subpackage, REST path | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/` | legal-vertical | Bank statement ↔ trust ledger reconciliation. Status `DRAFT, COMPLETED`. |
| Recurrence Rule | field `Task.recurrenceRule` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java:26` | tasks-time | RFC 5545 (iCal) RRULE for recurring tasks. |
| Recurring Project | event `RecurringProjectCreatedEvent`, schedule entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/` | projects | Project auto-created from a `ProjectTemplate` schedule. |
| Report Definition | backend entity, REST path `/api/report-definitions` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/ReportDefinition.java:17` | reporting-export | Parameterised report blueprint with PDF/CSV export. |
| Request Item | backend entity (information-request) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestItem.java:21` | information-requests | Single field/file inside an `InformationRequest`. |
| Request Template | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplate.java:16` | information-requests | Blueprint for `InformationRequest`. |
| Resource Allocation | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/capacity/ResourceAllocation.java:16` | capacity-resource-planning | Member×project×week hours. |
| Response Type | Java enum (information-request) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/ResponseType.java:3` | information-requests | What kind of answer an item expects (text, file, dropdown, etc.). |
| Retainer | backend entity `RetainerAgreement`, REST path `/api/retainers` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreement.java:18` | invoicing-billing | Pre-paid hour-bank agreement. UI label: "Mandate" (legal-za). Module-gated `retainer_agreements`. |
| Retainer Frequency | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerFrequency.java:5` | invoicing-billing | `MONTHLY, QUARTERLY, ANNUALLY`. |
| Retainer Period | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriod.java:18` | invoicing-billing | Single period of a retainer with consumption tracking. |
| Retainer Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerStatus.java:3` | invoicing-billing | Agreement state. |
| Retainer Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerType.java:3` | invoicing-billing | Style of retainer. |
| Retention Clock | field `Project.retentionClockStartedAt` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:24` | customer-lifecycle, projects | Anchors statutory retention period (e.g. 7 years from CLOSED). |
| Retention Policy | frontend type, OrgSettings field | `frontend/lib/types/customer.ts` (RetentionPolicy) | customer-lifecycle | Per-jurisdiction data retention duration. |
| Rollover Policy | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RolloverPolicy.java:3` | invoicing-billing | Whether unused retainer hours roll into the next period. |
| Rule Source | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/RuleSource.java:3` | automation-engine | `MANUAL, PACK, SYSTEM` — origin of an automation rule. |
| Saved View | backend entity, REST path `/api/views` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/view/SavedView.java:19` | custom-fields-views | Per-user filter+columns preset for a list page. Server-side SQL filtering. |
| Schedule (Project) | backend entity (`ProjectSchedule`), REST path `/api/schedules` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/projecttemplate/` | projects | Recurrence config for instantiating projects from a `ProjectTemplate`. Not a Calendar Event. |
| ScopedValue | Java 25 multitenancy carrier | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` | tenancy-provisioning | Replaces ThreadLocal. Holders: `TENANT_ID, MEMBER_ID, ORG_ID, ORG_ROLE, CUSTOMER_ID, PORTAL_CONTACT_ID, AUTOMATION_EXECUTION_ID, CAPABILITIES, GROUPS`. |
| Section 86 | TrustAccountType value | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountType.java` | legal-vertical | Legal Practice Act s.86 investment trust account. |
| Secret Store | port interface | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/SecretStore.java` | integrations-pack-system | Encrypted key-value store for adapter credentials. AES-GCM. |
| Setup Status | service, REST path `/api/projects/{id}/setup-status`, `/api/customers/{id}/setup-status` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/` | customer-lifecycle, projects | Readiness gate (custom-field completeness, prerequisites). |
| Signing State | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/signing/SigningState.java:4` | integrations-pack-system | Adapter-level e-sign progress. |
| Statement of Account | capability `GENERATE_STATEMENT_OF_ACCOUNT`, legal package `statement` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/` | legal-vertical | Customer-facing AR statement. |
| Subscription | backend entity (public schema) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java:19` | platform-administration | Org's Kazi platform subscription. NOT a customer-facing concept — Kazi has no plan tiers. |
| Subscription Payment | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionPayment.java:19` | platform-administration | Single payment against `Subscription`. |
| Subscription Status | Java enum (inner) | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/Subscription.java:277` | platform-administration | `TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE, SUSPENDED, GRACE_PERIOD, EXPIRED, LOCKED`. |
| Tag | backend entity, REST path `/api/tags` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tag/Tag.java:15` | custom-fields-views | Org-wide labelling of any entity. |
| Tariff Item | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffItem.java:17` | legal-vertical | Single LSSA tariff line. |
| Tariff Schedule | backend entity, frontend type, terminology override | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/tariff/TariffSchedule.java:19` | legal-vertical | LSSA tariff. Also UI label for `Rate Card` in legal-za. Overloaded — see divergence #7. |
| Task | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/Task.java:26` | tasks-time | Unit of work inside a Project. UI label: "Action Item" (legal-za). |
| Task Priority | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskPriority.java:4` | tasks-time | Priority tier. |
| Task Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskStatus.java:7` | tasks-time | `OPEN, IN_PROGRESS, DONE, CANCELLED`. |
| Tax Rate | backend entity, REST path `/api/tax-rates` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/TaxRate.java:19` | invoicing-billing | Per-tenant rate row. |
| Tax Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/TaxType.java:4` | invoicing-billing | `VAT, GST, SALES_TAX, NONE`. |
| Template Category | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateCategory.java:3` | documents-templates | Categorisation for `DocumentTemplate`. |
| Template Entity Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateEntityType.java:3` | documents-templates | Which entity a template targets. |
| Template Format | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateFormat.java:3` | documents-templates | Tiptap/DOCX/HTML. |
| Template Source | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplateSource.java:3` | documents-templates | `SYSTEM, PACK, CUSTOM`. There is also an `informationrequest` `TemplateSource` enum (divergence #3). |
| Tenant | concept (backend `multitenancy`), `TENANT_ID` ScopedValue | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` | tenancy-provisioning | Backend-internal name for the schema/Org. End-users say "Org"; backend says "Tenant". Same concept. |
| Tenant Profile | field `PortalSessionContext.tenantProfile`, frontend `verticalProfile` | `portal/hooks/use-portal-context.ts` | portal, vertical-profile-system | Vertical profile id for a tenant. |
| Terminology | frontend hook, OrgSettings field `terminologyNamespace` | `frontend/lib/terminology.tsx:28` | terminology, vertical-profile-system | Vertical-aware label translation. |
| Time Entry | backend entity, frontend type, REST path `/api/time-entries` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java:17` | tasks-time | Logged hours. UI labels: "Time Log" (consulting), "Time Recording" (legal). |
| Time Reminder | scheduler, settings | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/TimeReminderScheduler.java` | notifications-activity, tasks-time | Weekly nudge to log hours. |
| Tiptap | rendering technology | `architecture/ARCHITECTURE.md` | documents-templates | Rich-text JSON format used in `DocumentTemplate.content`. Never call this Thymeleaf. |
| Token Relay | gateway filter | `gateway/src/main/resources/application.yml:43` | gateway | Spring Cloud Gateway filter that injects OAuth2 access token as `Authorization: Bearer` to backend. |
| Trigger Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/TriggerType.java:3` | automation-engine | `TASK_STATUS_CHANGED, PROJECT_STATUS_CHANGED, CUSTOMER_STATUS_CHANGED, INVOICE_STATUS_CHANGED, TIME_ENTRY_CREATED, BUDGET_THRESHOLD_REACHED, DOCUMENT_ACCEPTED, INFORMATION_REQUEST_COMPLETED, PROPOSAL_SENT, FIELD_DATE_APPROACHING, SCHEDULED`. |
| Trust Account | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccount.java` | legal-vertical | Module `trust_accounting`, profile `legal-za`. Type `GENERAL/INVESTMENT/SECTION_86`. |
| Trust Account Status | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountStatus.java` | legal-vertical | `ACTIVE, CLOSED`. |
| Trust Account Type | Java enum | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountType.java` | legal-vertical | `GENERAL, INVESTMENT, SECTION_86`. |
| Trust Alert | frontend type | `frontend/lib/types/trust.ts` (TrustAlert) | legal-vertical | Dashboard alerts: `MATURING_INVESTMENT, OVERDUE_RECONCILIATION, AGING_APPROVAL`. |
| Trust Boundary Guard | backend service, ADR-276 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/...TrustBoundaryGuard` | legal-vertical, integrations-pack-system | Hard guard preventing trust-related invoices being pushed to Xero. |
| Trust Investment | backend entity | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/` | legal-vertical | Section 86 tracked investment of trust funds. |
| Trust Investment Status | frontend type | `frontend/lib/types/trust.ts` (TrustInvestmentStatus) | legal-vertical | `ACTIVE, MATURED, WITHDRAWN`. |
| Trust Reconciliation | backend, frontend, REST path | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/` | legal-vertical | Bank vs ledger match workflow. Status `DRAFT, COMPLETED`. |
| Trust Transaction | backend entity, frontend type | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/` | legal-vertical | Receipt/payment/transfer in a trust account. |
| Trust Transaction Type | frontend type | `frontend/lib/types/trust.ts:39` (TrustTransactionType) | legal-vertical | 10 variants including `DISBURSEMENT_PAYMENT`. |
| Unbilled Time | service, REST path `/api/invoices/unbilled-summary`, `/api/projects/{id}/unbilled-time` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/UnbilledTimeSummary.java` | invoicing-billing | Aggregated billable time not yet invoiced. |
| Vertical Profile | concept, registry, JSON, REST path `/api/vertical-profiles` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java` | vertical-profile-system | One of `consulting-generic, consulting-za, accounting-za, legal-za`. Drives modules + terminology. `legal-za` includes `consulting-za` features additively. |
| Visibility | field on `Comment`, `Document` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/Comment.java:15` | notifications-activity, documents-templates | `INTERNAL` vs `PORTAL` (or PUBLIC). Controls whether portal contacts see it. |
| Work Type | field `Project.workType` | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:24` | projects | Free-text classification (e.g. Audit, Litigation). |
| Workflow | (gap) — common product term | (none) | (n/a) | No `Workflow` entity. Translate to "Automation Rule" in code, ADRs, and UI. Retire "workflow" in conversation when an automation rule is meant. |
| Xero Adapter | concept, ADR-272 (Phase 71) | `adr/ADR-272-xero-only-accounting-adapter-v1.md` | integrations-pack-system | The single supported `IntegrationDomain.ACCOUNTING` adapter. One-way push. Idempotent via external reference (ADR-278). |

---

## Watch-words

Quick reference. Heard this in conversation? Use the canonical term instead.

| Heard in conversation | Canonical term | Reason |
|---|---|---|
| Account / Client / Lead | Customer (entity) — UI label "Client" allowed | Backend entity is `Customer`. "Lead" is not a status; the lifecycle stage is `PROSPECT`. |
| Activity record / Log entry | Audit Event (admin log) OR Activity Feed (cross-entity timeline) | Two distinct features — disambiguate. |
| Assistant | AI Assistant | "Assistant" alone is ambiguous (a Member could be a personal assistant). |
| Calendar event | Court Date OR Deadline OR Schedule (recurring) | Three distinct entities. |
| Client Portal | Portal | Code never says "Client Portal"; UI label OK. |
| Company | Organization (the tenant) OR `Customer.customerType=COMPANY` | Two unrelated meanings — disambiguate. |
| Engagement | Project (entity) — UI label "Engagement" only in accounting-za | Use `Project` in code/conversation; render "Engagement" only in accounting UI. |
| Engagement Letter | Proposal | UI label only (legal/accounting). Backend entity is `Proposal`. |
| Expense | Expense (generic) OR Disbursement (legal-vertical) | Legal vertical replaces Expense entirely. |
| Fee Note | Invoice | UI label only (legal). |
| Lead | Customer with `lifecycleStatus=PROSPECT` | Kazi has no separate Lead entity. |
| Mandate | Retainer | UI label only (legal). |
| Matter | Project (entity) OR `MatterClosure*` (legal-vertical-only services) | Backend uses Matter only inside `verticals/legal/closure/*`. |
| Permission | Capability | One atomic right. A bundle of capabilities is a Role. |
| Plan / Tier / Pro / Starter | (none) | Kazi has no plan tiers. Subscription has only `BillingMethod` + `SubscriptionStatus`. Retire. |
| Rate Card | Billing Rate (entity) — UI labels "Tariff Schedule" / "Fee Schedule" / "Billing Rates" per profile | "Rate Card" is a frontend label; the type is `BillingRate`. |
| Role | Org Role (org-scoped capability bundle) OR Project Role (per-project access tier) OR Portal Contact Role (PRIMARY/BILLING/GENERAL) | Three distinct enumerations. |
| Self-service signup / Registration | Access Request | Kazi has no public signup — admin-gated only. |
| Subscription (org concept) | Subscription = Kazi platform billing only | Customers do not have subscriptions in Kazi. |
| Tariff | Tariff Schedule (legal entity) OR Billing Rate (UI alias in legal vertical) | Disambiguate. |
| Tenant | Org / Organization (UI) — `TENANT_ID` (backend ScopedValue) | Same concept, different audience. |
| Thymeleaf | Tiptap | Document rendering uses Tiptap — never Thymeleaf. |
| Time Log / Time Recording | Time Entry | UI labels per profile; entity is `TimeEntry`. |
| User | Member (in-org) OR Portal Contact (customer-side) OR Platform Admin (Kazi staff) | Three different identities. |
| Workflow | Automation Rule | No `Workflow` entity. |
| Workspace | Org / Organization | Kazi never uses "Workspace". |

---

## Known divergences

These are real inconsistencies in the codebase or vocabulary that future architecture work or refactoring will address. Each is numbered for cross-reference from the glossary table above.

1. **PortalContact (backend) vs "Customer Contact" (UI in places)** — entity name should be authoritative; UI inconsistencies should be normalised to "Portal Contact" or "Contact" with disambiguation.
2. **Three `BillingStatus` enums** (TimeEntry, Expense, Disbursement) and three `PaymentStatus` enums (integration, SubscriptionPayment inner, ledger). Always qualify when discussing.
3. **Two `TemplateSource` enums** — `template/TemplateSource.java` (DocumentTemplate) and `informationrequest/TemplateSource.java`. Same values typically, distinct types.
4. **`Customer.customerType=TRUST`** clashes verbally with trust accounting — explicit disambiguation needed in any feature naming.
5. **`Project.status=CLOSED`** is legal-vertical only and is non-terminal (supports reopen). Most agents assume CLOSED is terminal — it is not.
6. **`clerkUserId` field** on Member persists despite Clerk being fully removed. Holds Keycloak `sub`. Rename is deferred but the divergence is real.
7. **"Tariff Schedule"** is overloaded: it is an entity in legal-vertical AND it is the legal-za UI label for the generic Billing Rate. Two related but distinct meanings.
8. **`Workflow`** is a frequent product/conversation term with no code anchor — agents must translate to Automation Rule.

---

For module-to-context mapping, see [`10-bounded-contexts.md`](./10-bounded-contexts.md).
For active architecture decisions, see [`90-adr-index.md`](./90-adr-index.md).
