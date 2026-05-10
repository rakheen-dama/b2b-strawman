# A1 — Kazi Backend Structural Map

**Generated:** 2026-05-10
**Source root:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/`
**Spring Boot:** 4.0.2 / Java 25 / Hibernate 7

---

## 1. Top-Level Package Map

Base package: `io.b2mash.b2b.b2bstrawman`

- **acceptance** — Document acceptance / lightweight e-signing workflow (AcceptanceRequest lifecycle, expiry processor).
- **accessrequest** — Admin-gated org provisioning: public OTP sign-up flow and platform-admin approval pipeline.
- **assistant** — In-app AI assistant (LLM provider abstraction, tool framework, SSE streaming chat API).
- **audit** — Append-only audit event store, query API, and domain-event listener that writes audit rows.
- **automation** — Workflow automation engine: rules, trigger matching, condition evaluation, action executors, scheduled/delayed execution.
- **billing** — Platform subscription management (Subscription, SubscriptionPayment, PayFast adapter, enforcement scheduler, status cache).
- **billingrun** — Bulk billing orchestration: BillingRun entity, preview/cherry-pick, batch invoice generation, batch send.
- **capacity** — Resource planning: MemberCapacity and ResourceAllocation entities, utilization service.
- **checklist** — Compliance checklist engine: ChecklistTemplate and ChecklistInstance entities, pack instantiation.
- **clause** (inside `template`) — Document clauses library (DocumentClause entity), template-clause associations.
- **comment** — Threaded comments on projects/tasks (Comment entity, CRUD, visibility control).
- **compliance** — Customer lifecycle orchestration: dormancy detection, retention, data-subject requests, lifecycle transitions. Also holds pack seeders for compliance packs.
- **config** — Spring configuration beans (Security, Hibernate multitenancy, S3, Flyway, Resilience4j).
- **customer** — Customer entity (with lifecycle status), customer-project linking, CRUD, custom fields, lifecycle transitions.
- **dashboard** — Project health scoring, company KPIs, personal dashboard, cross-project activity feed.
- **dev** — Thymeleaf dev harness for portal testing (profile-gated: `local`/`dev` only).
- **document** — Document upload/download, S3 key management, scope (PROJECT/CUSTOMER), visibility.
- **event** — Sealed `DomainEvent` interface + ~35 record implementations (the backbone of inter-module wiring).
- **exception** — Shared semantic exceptions (ResourceNotFoundException, ForbiddenException, InvalidStateException, etc.).
- **expense** — Expense entity, CRUD, categories, billable flag, invoice linking.
- **fielddefinition** — Custom fields engine: FieldDefinition, FieldGroup entities, pack seeder, auto-apply logic.
- **informationrequest** — Client information-request workflow: RequestTemplate, InformationRequest, RequestItem entities, reminder scheduler.
- **integration** — Integration port registry: OrgIntegration entity, IntegrationGuardService, SecretStore port, payment/email/accounting/AI/KYC domains.
- **invitation** — PendingInvitation entity and service for application-managed role invitations.
- **invoice** — Invoice and InvoiceLine entities, lifecycle (DRAFT→APPROVED→SENT→PAID/VOID), payment events, PDF preview.
- **member** — Member entity, ProjectMember, MemberFilter (servlet filter), OrgMemberController, ProjectAccessService.
- **multitenancy** — Core multitenancy infrastructure: RequestScopes (ScopedValue), OrgSchemaMapping, TenantFilter, SchemaBasedMultiTenantConnectionProvider, TenantScopedRunner.
- **notification** — In-app Notification entity, NotificationPreference, NotificationService (event listener), time-reminder scheduler.
- **orgrole** — OrgRole entity, Capability enum, RequiresCapability annotation/interceptor, OrgRoleService/Controller.
- **packs** — Pack catalog service, PackInstall entity, PackInstaller SPI (field packs, compliance packs, clause packs, template packs, etc.).
- **portal** — Customer portal: PortalContact, MagicLinkToken, PortalAuthController, portal read-model event handlers, portal-facing API controllers.
- **prerequisite** — Prerequisite checking service (validates required custom fields and structural preconditions before lifecycle transitions).
- **projecttemplate** — ProjectTemplate entity, schedule entity (recurring project schedules), template instantiation service, schedule execution scheduler.
- **proposal** — Proposal entity, lifecycle (DRAFT→SENT→ACCEPTED/DECLINED/EXPIRED), acceptance orchestration, expiry processor.
- **provisioning** — Organization entity (global schema), TenantProvisioningService (Flyway DDL, seed data orchestration), ProvisioningController.
- **reporting** — ReportDefinition entity, report execution framework, CSV/PDF export, report preview.
- **retainer** — RetainerAgreement and RetainerPeriod entities, consumption tracking, period close, invoice generation.
- **s3** — S3 storage port (StorageService interface) and concrete S3 adapter + InMemoryStorageService for tests.
- **security** — ApiKeyAuthFilter, ClerkJwtAuthenticationConverter, MemberFilter, PlatformAdminFilter, JwtUtils, Roles.
- **seeder** — Generic seeder infrastructure (AbstractPackSeeder, RatePackSeeder, SchedulePackSeeder, ProjectTemplatePackSeeder).
- **settings** — OrgSettings entity (single-row per tenant), OrgSettingsController (branding, compliance, integrations flags, time-reminder config).
- **setupstatus** — Contextual action/readiness services: ProjectSetupStatus, CustomerReadiness, DocumentGenerationReadiness, UnbilledTimeSummary.
- **tag** — Tag and EntityTag entities, EntityTagService, batch tag loading, tag filter utilities.
- **task** — Task entity (with recurrence fields), TaskController, TaskService, lifecycle state machine.
- **tax** — TaxRate entity, tax calculation engine, invoice line tax application.
- **template** — DocumentTemplate, GeneratedDocument, template rendering pipeline (Tiptap JSON + Mustache), DOCX merge service.
- **timeentry** — TimeEntry entity, billable/rate snapshot fields, CRUD, bulk weekly grid endpoint.
- **verticals** — Vertical profile system: VerticalProfileRegistry (loads JSON profiles from classpath), VerticalProfileReconciliationSeeder, module registry.
  - **verticals.legal** — Legal vertical: tariff schedules (TariffSchedule, TariffItem), LegalTariffSeeder.
- **view** — SavedView entity, ViewFilterHelper (server-side SQL filter application for saved views).

### Child-package breakdown (selected significant packages)

- **automation/**
  - `config/` — Action-type configuration records (ActionFailure, etc.)
  - `fielddate/` — FieldDateScannerScheduler and FieldDateApproachingEvent
  - `dto/` — AutomationDtos

- **assistant/**
  - `provider/` — LlmChatProvider interface, AnthropicChatProvider, LlmChatProviderRegistry
  - `specialist/` — SpecialistRegistry, SystemPromptBuilder, ContextRef
  - `tool/` — AssistantToolRegistry, TenantToolContext, read/write tool implementations

- **integration/**
  - `secret/` — SecretStore port + EncryptedDatabaseSecretStore implementation
  - `storage/` — StorageService port (lives in `s3/`)

- **billing/**
  - `payfast/` — PlatformPayFastService (platform-level PayFast subscription payments)

- **portal/**
  - `readmodel/` — Event listeners that sync domain entities into portal read-model tables

- **compliance/**
  - `dataprotection/` — ProcessingActivity CRUD, PAIA manual generation, anonymization/export extensions

---

## 2. Entity Catalogue

All entities are tenant-scoped unless noted as **shared (public schema)**.

### `provisioning`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Organization` | `organizations` (public) | `externalOrgId`, `name`, `provisioningStatus` | none | shared |

### `multitenancy`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `OrgSchemaMapping` | `org_schema_mapping` (public) | `externalOrgId`, `schemaName` | none | shared |

`→ multitenancy/OrgSchemaMapping.java:14`

### `billing`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Subscription` | `subscriptions` (public) | `organizationId`, `subscriptionStatus`, `trialEndsAt`, `payfastToken` | none | shared |
| `SubscriptionPayment` | `subscription_payments` (public) | `subscriptionId`, `payfastPaymentId`, `amountCents`, `status`, `paymentDate` | belongs to Subscription | shared |

`→ billing/Subscription.java:19`, `billing/SubscriptionPayment.java:19`

### `accessrequest`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `AccessRequest` | `access_requests` (public) | `email`, `fullName`, `organizationName`, `status`, `otpHash`, `otpExpiresAt`, `reviewedBy` | none | shared |

`→ accessrequest/AccessRequest.java:18`

### `member`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Member` | `members` | `clerkUserId`, `email`, `name`, `orgRoleId`, `capabilityOverrides` | `@ManyToOne OrgRole` | tenant |
| `ProjectMember` | `project_members` | `projectId`, `memberId`, `projectRole`, `addedBy` | FK to member/project | tenant |

`→ member/Member.java:22`, `member/ProjectMember.java:14`

### `invitation`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `PendingInvitation` | `pending_invitations` | `email`, `status`, `expiresAt`, `acceptedAt` | `@ManyToOne OrgRole`, `@ManyToOne Member invitedBy` | tenant |

`→ invitation/PendingInvitation.java:22`

### `orgrole`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `OrgRole` | `org_roles` | `name`, `slug`, `isSystem`, `capabilities` | `@ElementCollection org_role_capabilities` | tenant |

`→ orgrole/OrgRole.java:23`

### `project`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Project` | `projects` | `name`, `status`, `customerId`, `dueDate`, `createdBy`, `closedAt`, `retentionClockStartedAt`, `priority`, `workType` | FK customerId→customers | tenant |

`→ project/Project.java:24`

### `customer`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Customer` | `customers` | `name`, `email`, `lifecycleStatus`, `customerType`, `createdBy`, `idNumber`, `offboardedAt` | FK→members | tenant |
| `CustomerProject` | `customer_projects` | `customerId`, `projectId`, `linkedBy` | FK→customers, projects | tenant |

`→ customer/Customer.java:23`, `customer/CustomerProject.java:14`

### `task`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Task` | `tasks` | `projectId`, `title`, `status`, `priority`, `assigneeId`, `dueDate`, `recurrenceRule`, `parentTaskId`, `estimatedHours` | FK→projects, members | tenant |

`→ task/Task.java:26`

### `timeentry`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `TimeEntry` | `time_entries` | `taskId`, `memberId`, `date`, `durationMinutes`, `billable`, `billingRateSnapshot`, `costRateSnapshot`, `invoiceId` | FK→tasks, members, invoices | tenant |

`→ timeentry/TimeEntry.java:17`

### `document`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Document` | `documents` | `projectId`, `customerId`, `fileName`, `s3Key`, `status`, `scope`, `visibility`, `uploadedBy` | FK→projects, customers | tenant |

`→ document/Document.java:16`

### `comment`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Comment` | `comments` | `entityType`, `entityId`, `projectId`, `authorMemberId`, `body`, `visibility`, `parentId`, `source` | FK→members, projects | tenant |

`→ comment/Comment.java:15`

### `notification`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Notification` | `notifications` | `recipientMemberId`, `type`, `title`, `referenceEntityType`, `referenceEntityId`, `isRead` | FK→members | tenant |
| `NotificationPreference` | `notification_preferences` | `memberId`, `notificationType`, `inAppEnabled`, `emailEnabled` | FK→members | tenant |

`→ notification/Notification.java:13`, `notification/NotificationPreference.java:13`

### `audit`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `AuditEvent` | `audit_events` | `eventType`, `entityType`, `entityId`, `actorId`, `actorType`, `source`, `ipAddress`, `details (jsonb)` | immutable, no FK | tenant |

`→ audit/AuditEvent.java:29`

### `invoice`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Invoice` | `invoices` | `customerId`, `invoiceNumber`, `status`, `currency`, `subtotal`, `taxAmount`, `total`, `dueDate`, `paidAt` | FK→customers | tenant |
| `InvoiceLine` | `invoice_lines` | `invoiceId`, `lineType`, `lineSource`, `timeEntryId`, `retainerPeriodId`, `expenseId`, `tariffItemId`, `disbursementId` | FK→invoice | tenant |

`→ invoice/Invoice.java:24`, `invoice/InvoiceLine.java:19`

### `expense`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Expense` | `expenses` | `projectId`, `taskId`, `memberId`, `date`, `amount`, `currency`, `category`, `billable`, `invoiceId`, `markupPercent` | FK→projects, members, invoices | tenant |

`→ expense/Expense.java:19`

### `retainer`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `RetainerAgreement` | `retainer_agreements` | `customerId`, `type`, `status`, `frequency`, `startDate`, `allocatedHours`, `periodFee`, `rolloverPolicy` | FK→customers | tenant |
| `RetainerPeriod` | `retainer_periods` | `agreementId`, `periodStart`, `periodEnd`, `status`, `consumedHours`, `rolloverHoursIn`, `invoiceId` | FK→retainer_agreements | tenant |

`→ retainer/RetainerAgreement.java:18`, `retainer/RetainerPeriod.java:18`

### `tax`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `TaxRate` | `tax_rates` | `name`, `rate`, `isDefault`, `isExempt`, `active`, `sortOrder` | none | tenant |

`→ tax/TaxRate.java:19`

### `billingrun`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `BillingRun` | `billing_runs` | `name`, `status`, `periodFrom`, `periodTo`, `currency`, `totalCustomers`, `totalInvoices`, `totalAmount` | none | tenant |

`→ billingrun/BillingRun.java:18`

### `proposal`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Proposal` | `proposals` | `proposalNumber`, `title`, `customerId`, `status`, `feeModel`, `fixedFeeAmount`, `portalContactId`, `expiresAt` | FK→customers, portal_contacts | tenant |

`→ proposal/Proposal.java:31`

### `acceptance`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `AcceptanceRequest` | `acceptance_requests` | `generatedDocumentId`, `portalContactId`, `customerId`, `status`, `requestToken`, `expiresAt`, `acceptedAt` | FK→generated_documents, portal_contacts | tenant |

`→ acceptance/AcceptanceRequest.java:22`

### `template`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `DocumentTemplate` | `document_templates` | `name`, `slug`, `category`, `primaryEntityType`, `content (jsonb)`, `source`, `packId` | none | tenant |
| `GeneratedDocument` | `generated_documents` | `templateId`, `primaryEntityType`, `primaryEntityId`, `s3Key`, `documentId`, `generatedBy` | FK→document_templates | tenant |

`→ template/DocumentTemplate.java:22`, `template/GeneratedDocument.java:22`

### `portal`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `PortalContact` | `portal_contacts` | `orgId`, `customerId`, `email`, `displayName`, `role`, `status` | FK→customers | tenant |
| `MagicLinkToken` | `magic_link_tokens` | `portalContactId`, `tokenHash`, `expiresAt`, `usedAt`, `createdIp` | FK→portal_contacts | tenant |

`→ portal/PortalContact.java:16`, `portal/MagicLinkToken.java:19`

### `automation`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `AutomationRule` | `automation_rules` | `name`, `enabled`, `triggerType`, `triggerConfig (jsonb)`, `conditions (jsonb)`, `source`, `templateSlug` | none | tenant |
| `AutomationAction` | `automation_actions` | `ruleId`, `sortOrder`, `actionType`, `actionConfig (jsonb)`, `delayDuration`, `delayUnit` | FK→automation_rules | tenant |
| `AutomationExecution` | `automation_executions` | `ruleId`, `triggerEventType`, `conditionsMet`, `status`, `startedAt`, `errorMessage` | FK→automation_rules | tenant |

`→ automation/AutomationRule.java:20`, `automation/AutomationAction.java:19`, `automation/AutomationExecution.java:19`

### `fielddefinition`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `FieldDefinition` | `field_definitions` | `entityType`, `name`, `slug`, `fieldType`, `required`, `options (jsonb)`, `packId` | none | tenant |
| `FieldGroup` | `field_groups` | `entityType`, `name`, `slug`, `autoApply`, `packId`, `dependencies (jsonb)` | none | tenant |

`→ fielddefinition/FieldDefinition.java:22`, `fielddefinition/FieldGroup.java:19`

### `tag`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `Tag` | `tags` | `name`, `slug`, `color` | none | tenant |
| `EntityTag` | `entity_tags` | `tagId`, `entityType`, `entityId` | FK→tags | tenant |

`→ tag/Tag.java:15`, `tag/EntityTag.java:14`

### `view`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `SavedView` | `saved_views` | `entityType`, `name`, `filters (jsonb)`, `columns (jsonb)`, `shared`, `createdBy` | none | tenant |

`→ view/SavedView.java:19`

### `checklist`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `ChecklistTemplate` | `checklist_templates` | `name`, `slug`, `customerType`, `source`, `packId`, `autoInstantiate` | none | tenant |
| `ChecklistInstance` | `checklist_instances` | `templateId`, `customerId`, `status`, `startedAt`, `completedAt` | FK→checklist_templates, customers | tenant |

`→ checklist/ChecklistTemplate.java:16`, `checklist/ChecklistInstance.java:14`

### `projecttemplate`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `ProjectTemplate` | `project_templates` | `name`, `namePattern`, `billableDefault`, `source`, `active`, `requiredCustomerFieldIds (jsonb)`, `requestTemplateId` | none | tenant |

`→ projecttemplate/ProjectTemplate.java:18`

### `informationrequest`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `RequestTemplate` | `request_templates` | `name`, `source`, `packId`, `active` | none | tenant |
| `InformationRequest` | `information_requests` | `requestNumber`, `requestTemplateId`, `customerId`, `portalContactId`, `status`, `dueDate`, `reminderIntervalDays` | FK→templates, customers, portal_contacts | tenant |
| `RequestItem` | `request_items` | `requestId`, `templateItemId`, `name`, `responseType`, `required`, `status` | FK→information_requests | tenant |

`→ informationrequest/RequestTemplate.java:16`, `informationrequest/InformationRequest.java:22`, `informationrequest/RequestItem.java:21`

### `reporting`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `ReportDefinition` | `report_definitions` | `name`, `slug`, `category`, `parameterSchema (jsonb)`, `columnDefinitions (jsonb)`, `templateBody`, `isSystem` | none | tenant |

`→ reporting/ReportDefinition.java:17`

### `capacity`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `MemberCapacity` | `member_capacities` | `memberId`, `weeklyHours`, `effectiveFrom`, `effectiveTo` | FK→members | tenant |
| `ResourceAllocation` | `resource_allocations` | `memberId`, `projectId`, `weekStart`, `allocatedHours` | FK→members, projects | tenant |

`→ capacity/MemberCapacity.java:16`, `capacity/ResourceAllocation.java:16`

### `integration`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `OrgIntegration` | `org_integrations` | `domain`, `providerSlug`, `enabled`, `configJson (jsonb)`, `keySuffix` | none | tenant |

`→ integration/OrgIntegration.java:20`

### `packs`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `PackInstall` | `pack_install` | `packId`, `packType`, `packVersion`, `packName`, `installedAt`, `itemCount` | none | tenant |

`→ packs/PackInstall.java:17`

### `settings`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `OrgSettings` | `org_settings` | `defaultCurrency`, `brandColor`, `logoS3Key`, `dormancyThresholdDays`, `fieldPackStatus (jsonb)`, `templatePackStatus (jsonb)` (and 20+ additional fields) | none | tenant |

`→ settings/OrgSettings.java:34`

### `verticals.legal.tariff`
| Entity | Table | Key fields | Relationships | Scope |
|---|---|---|---|---|
| `TariffSchedule` | `tariff_schedules` | `name`, `category`, `courtLevel`, `effectiveFrom`, `isActive`, `isSystem` | `@OneToMany TariffItem` | tenant |
| `TariffItem` | `tariff_items` | `scheduleId`, `itemNumber`, `section`, `description`, `amount`, `unit` | `@ManyToOne TariffSchedule` | tenant |

`→ verticals/legal/tariff/TariffSchedule.java:19`, `verticals/legal/tariff/TariffItem.java:17`

**Total entity count (approximate): 55–60 JPA entity classes across 30+ packages.**

---

## 3. REST Surface

### Internal API (API-key secured — `X-API-KEY` header)

| Controller | Base path | Endpoints |
|---|---|---|
| `ProvisioningController` | `/internal/orgs` | `POST /provision → provisionTenant` |
| `MemberSyncController` | `/internal/members` | `POST /sync → syncMember`, `DELETE /{clerkUserId} → deleteMember`, `GET /stale → listStaleMembers` |

`→ provisioning/ProvisioningController.java:16`, `member/MemberSyncController.java:22`

### Public API (JWT-authenticated, capability-gated)

| Controller | Base path | Key endpoints | Count |
|---|---|---|---|
| `ProjectController` | `/api/projects` | GET list, POST create, GET /{id}, PUT /{id}, DELETE /{id}, PATCH /{id}/status, POST /{id}/complete, POST /{id}/archive, POST /{id}/close, POST /{id}/reopen, PUT /{id}/custom-fields, PUT /{id}/field-groups, GET /{id}/setup-status, GET /{id}/unbilled-time, GET /{id}/upcoming-deadlines, GET /{id}/tags, PUT /{id}/tags | ~18 |
| `TaskController` | `/api/projects/{projectId}/tasks` + `/api/tasks` | POST create, GET list, GET /{id}, PUT /{id}, DELETE /{id}, PATCH /{id}/status, POST /{id}/claim, POST /{id}/release, POST /{id}/complete, POST /{id}/cancel, POST /{id}/reopen, PUT /{id}/custom-fields, PUT /{id}/field-groups, GET /{id}/tags, PUT /{id}/tags | ~16 |
| `CustomerController` | `/api/customers` | GET list, POST create, GET /{id}, PUT /{id}, DELETE /{id}, POST /{id}/transition, GET /{id}/projects, GET /{id}/setup-status, GET /{id}/unbilled-time, GET /{id}/portal-contacts, GET /{id}/fica-status, PUT /{id}/custom-fields, PUT /{id}/field-groups, GET /{id}/tags, PUT /{id}/tags, GET /{id}/dormancy-check | ~18 |
| `InvoiceController` | `/api/invoices` | POST create, GET list, GET /{id}, PUT /{id}, DELETE /{id}, GET /{id}/preview, GET /unbilled-summary, POST /{id}/approve, POST /{id}/send, POST /{id}/record-payment, POST /{id}/void, POST /{id}/lines, PUT /{id}/lines/{lineId}, DELETE /{id}/lines/{lineId}, POST /{id}/disbursement-lines, PUT /{id}/custom-fields, PUT /{id}/field-groups, GET /payment-events | ~18 |
| `BillingRunController` | `/api/billing-runs` | POST create, GET list, GET /{id}, GET /{id}/items, GET /{id}/time-entries, GET /{id}/expenses, GET /{id}/retainer-periods, POST /{id}/generate, POST /{id}/cancel, POST /{id}/approve, POST /{id}/batch-send, POST /preview, PUT /{id}/entry-selections | ~14 |
| `DocumentController` | `/api/projects/{projectId}/documents` + `/api/documents` | POST upload-init, POST /{id}/confirm, GET /{id}/presign-download, DELETE /{id}, GET list | ~5 |
| `TimeEntryController` | `/api/time-entries` + `/api/projects/{projectId}/time` | POST create, GET list (by task/member), PUT /{id}, DELETE /{id}, POST /bulk, GET /calendar | ~10 |
| `ExpenseController` | `/api/projects/{projectId}/expenses` + `/api/expenses` | POST create, GET list, GET /{id}, PUT /{id}, DELETE /{id} | ~6 |
| `RetainerController` | `/api/retainers` | Full CRUD + period management + consumption endpoints | ~12 |
| `ProposalController` | `/api/proposals` | CRUD + send + accept/decline + portal sync | ~10 |
| `AcceptanceController` | `/api/acceptance-requests` | CRUD + send + revoke + certificate download | ~8 |
| `AutomationRuleController` | `/api/automation-rules` | GET list, POST create, GET /{id}, PUT /{id}, DELETE /{id}, POST /{id}/toggle, POST /{id}/duplicate, GET /templates, POST /{id}/test, GET /executions, GET /executions/{id} | ~11 |
| `ReportingController` | `/api/report-definitions` | GET list, GET /{slug}, POST /{slug}/execute, GET /{slug}/preview, GET /{slug}/export/pdf, GET /{slug}/export/csv | 6 |
| `DashboardController` | (no base — path per method) | GET /api/projects/{id}/health, GET /api/projects/{id}/task-summary, GET /api/projects/{id}/member-hours, GET /api/dashboard/kpis, GET /api/dashboard/project-health, GET /api/dashboard/team-workload, GET /api/dashboard/member-hours, GET /api/dashboard/activity, GET /api/dashboard/personal, GET /api/dashboard/proposal-summary | ~10 |
| `OrgSettingsController` | `/api/settings` | GET, PUT, POST /logo, DELETE /logo, PATCH /compliance, PATCH /time-reminder, PATCH /portal-notification, PATCH /module, GET /terminology | ~10 |
| `OrgMemberController` | `/api/members` | GET list, GET /{id}/capabilities, PUT /{id}/role | 3 |
| `OrgRoleController` | `/api/org-roles` | GET list, GET /{id}, POST create, PUT /{id}, DELETE /{id} | 5 |
| `TagController` | `/api/tags` | GET list, POST create, GET /{id}, PUT /{id}, DELETE /{id} | 5 |
| `SavedViewController` | `/api/views` | GET list, POST create, PUT /{id}, DELETE /{id} | 4 |
| `FieldDefinitionController` | `/api/field-definitions` + `/api/field-groups` | Full CRUD for both | ~10 |
| `TaxController` | `/api/tax-rates` | CRUD | 5 |
| `IntegrationController` | `/api/integrations` | GET catalog, GET /{domain}, POST /{domain}/configure, POST /{domain}/enable, POST /{domain}/disable, DELETE /{domain} | 6 |
| `PackCatalogController` | `/api/packs` | GET catalog, POST /{type}/{id}/install | 2 |
| `AuditController` | `/api/audit-events` | GET list (paginated, filtered), GET /{id} | 2 |
| `ChecklistController` | `/api/checklist-templates` + `/api/checklist-instances` | Template CRUD, instance lifecycle | ~8 |
| `ProjectTemplateController` | `/api/project-templates` | CRUD + instantiate + schedule CRUD | ~10 |
| `InformationRequestController` | `/api/information-requests` + `/api/request-templates` | Full lifecycle for both | ~12 |
| `CapacityController` | `/api/capacity` + `/api/allocations` | CRUD for member capacity and allocations, utilization grid | ~8 |
| `VerticalProfileController` | `/api/vertical-profiles` | GET available profiles, GET current | 2 |
| `AssistantController` | `/api/assistant` | `POST /chat → chat (SSE stream)` | 1 |

### Portal API (portal-JWT authenticated)
| Controller | Base path | Endpoints |
|---|---|---|
| `PortalAuthController` | `/portal/auth` | `POST /request-link`, `POST /exchange` |
| `PortalProjectController` | `/portal/projects` | GET list, GET /{id}, GET /{id}/documents, GET /{id}/comments |
| `PortalCommentController` | `/portal/comments` | POST create |
| `PortalInvoiceController` | `/portal/invoices` | GET list, GET /{id}, GET /{id}/pdf |
| `PortalAcceptanceController` | `/portal/acceptance-requests` | GET pending, GET /{token}, POST /{token}/accept |
| `PortalProposalController` | `/portal/proposals` | GET list, GET /{token}, POST /{token}/accept, POST /{token}/decline |
| `PortalInformationRequestController` | `/portal/information-requests` | GET list, GET /{id}, POST /{id}/items/{itemId}/submit |
| `PortalBrandingController` | `/portal/branding` | GET branding |
| `PortalProfileController` | `/portal/profile` | GET, PUT |

### Platform-admin API (JWT + platform-admins group)
| Controller | Base path | Endpoints |
|---|---|---|
| `PlatformAdminController` | `/api/platform-admin/access-requests` | GET list, POST /{id}/approve, POST /{id}/reject |
| `PlatformAdminBillingController` | `/api/platform-admin/billing` | GET orgs, GET /{orgId}/subscription, POST /{orgId}/subscription/transition, POST /{orgId}/subscription/admin-adjust |
| `PlatformAdminDemoController` | `/api/platform-admin/demo` | POST /provision, DELETE /{orgId} |

**Ballpark total REST endpoints: ~260–290 across 40+ controllers.**

---

## 4. Spring Application Events

### Domain Event Backbone

The `event.DomainEvent` sealed interface (with ~35 record implementations) is the cross-module communication backbone. All domain events flow through `ApplicationEventPublisher`.

**Publishers (services that call `eventPublisher.publishEvent(...)`):**

| Package | Service/Class | Events Published |
|---|---|---|
| `project` | `ProjectService` | `ProjectCompletedEvent`, `ProjectArchivedEvent`, `ProjectReopenedEvent` |
| `task` | `TaskService` | `TaskStatusChangedEvent`, `TaskCompletedEvent`, `TaskCancelledEvent`, `TaskReopenedEvent`, `TaskAssignedEvent`, `TaskClaimedEvent`, `TaskRecurrenceCreatedEvent` |
| `comment` | `CommentService` | `CommentCreatedEvent`, `CommentUpdatedEvent`, `CommentDeletedEvent`, `CommentVisibilityChangedEvent` |
| `document` | `DocumentService` | `DocumentUploadedEvent` |
| `template` | `DocumentGenerationService` | `DocumentGeneratedEvent` |
| `invoice` | `InvoiceService` | `InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoicePaidEvent`, `InvoiceVoidedEvent`, `InvoicePaymentReversedEvent`, `InvoicePaymentPartiallyReversedEvent` |
| `member` | `ProjectMemberService` | `MemberAddedToProjectEvent` |
| `acceptance` | `AcceptanceService` | `AcceptanceRequestSentEvent`, `AcceptanceRequestViewedEvent`, `AcceptanceRequestAcceptedEvent`, `AcceptanceRequestRevokedEvent`, `AcceptanceRequestExpiredEvent` |
| `proposal` | `ProposalService`, `ProposalExpiryProcessor` | `ProposalSentEvent`, `ProposalExpiredEvent` |
| `informationrequest` | `InformationRequestService` | `InformationRequestSentEvent`, `InformationRequestCompletedEvent`, `InformationRequestCancelledEvent`, `InformationRequestDraftCreatedEvent`, `RequestItemSubmittedEvent`, `RequestItemAcceptedEvent`, `RequestItemRejectedEvent` |
| `expense` | `ExpenseService` | `ExpenseCreatedEvent`, `ExpenseDeletedEvent` |
| `timeentry` | `TimeEntryService` | `TimeEntryChangedEvent` |
| `compliance` | `CustomerLifecycleService` | `CustomerStatusChangedEvent` |
| `projecttemplate` | `ProjectScheduleService` | `RecurringProjectCreatedEvent`, `ScheduleCompletedEvent`, `ScheduleSkippedEvent` |
| `invoice` | `InvoiceService` | `BudgetThresholdEvent` (when budget threshold crossed) |
| `automation` | `AutomationActionExecutor` | `FieldDateApproachingEvent` (via fielddate scanner) |

**Consumers (`@EventListener` / `@TransactionalEventListener`):**

| Class | Events Handled |
|---|---|
| `automation/AutomationEventListener` | `DomainEvent` (all — routes to trigger matching engine) |
| `notification/NotificationService` | `CommentCreatedEvent`, `TaskAssignedEvent`, `TaskClaimedEvent`, `InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoicePaidEvent`, `InvoiceVoidedEvent`, `DocumentUploadedEvent`, `DocumentGeneratedEvent`, `MemberAddedToProjectEvent`, `ProjectCompletedEvent`, `ProjectArchivedEvent`, `BudgetThresholdEvent`, `AcceptanceRequestAcceptedEvent`, `TaskRecurrenceCreatedEvent`, schedule events |
| `portal` read-model listeners | `DocumentGeneratedEvent`, `InvoiceSentEvent`, `InvoicePaidEvent`, `AcceptanceRequestSentEvent`, `AcceptanceRequestAcceptedEvent`, `ProposalSentEvent`, `ProposalExpiredEvent`, task/request events — sync into portal read-model tables |
| `audit/AuditEventListener` (implicit in services) | Services call `auditService.log(...)` directly rather than listening |

`→ event/DomainEvent.java:17`, `automation/AutomationEventListener.java:25`, `notification/NotificationService.java:50`

---

## 5. Scheduled Jobs

All schedulers use `@Scheduled(fixedDelay=...)` and iterate all tenants via `TenantScopedRunner.forEachTenant(...)`.

| Class | Schedule | Responsibility |
|---|---|---|
| `automation/AutomationScheduler#pollDelayedActions` | fixedDelay 15 min | Executes SCHEDULED-status action executions past their `scheduledFor` time |
| `automation/AutomationScheduler#pollScheduledTriggers` | fixedDelay 60 s | Fires SCHEDULED trigger rules whose cron expression is due |
| `automation/fielddate/FieldDateScannerScheduler` | (daily — exact TBD) | Scans entity custom fields for DATE fields approaching a deadline; publishes `FieldDateApproachingEvent` |
| `acceptance/AcceptanceExpiryProcessor#processExpired` | fixedDelay 1 h | Expires SENT acceptance requests past their `expiresAt` |
| `proposal/ProposalExpiryProcessor` | fixedDelay 1 h | Expires SENT proposals past their `expiresAt`; publishes `ProposalExpiredEvent` |
| `projecttemplate/ProjectScheduleRunner` (approx name) | daily | Fires due recurring project schedules; instantiates projects from templates |
| `informationrequest/InformationRequestReminderScheduler` | daily | Sends reminder emails for open information requests past their `lastReminderSentAt + reminderIntervalDays` |
| `notification/TimeReminderScheduler` | weekly (per `OrgSettings.timeReminderDay`) | Sends time-logging reminder notifications to members who haven't logged hours |
| `compliance/DormancyScheduler` | daily | Identifies customers that have crossed dormancy threshold; triggers lifecycle events |
| `billing/SubscriptionEnforcementScheduler` | daily | Evaluates trial/grace expiry, transitions PAST_DUE → SUSPENDED, etc. |

`→ automation/AutomationScheduler.java:24`, `acceptance/AcceptanceExpiryProcessor.java:14`, `proposal/ProposalExpiryProcessor.java:25`

---

## 6. Cross-Cutting Infrastructure

### Tenancy Entry Points

| File | Purpose |
|---|---|
| `multitenancy/RequestScopes.java` | Java 25 ScopedValue holders: `TENANT_ID`, `MEMBER_ID`, `ORG_ID`, `ORG_ROLE`, `CUSTOMER_ID`, `PORTAL_CONTACT_ID`, `AUTOMATION_EXECUTION_ID`, `CAPABILITIES`, `GROUPS` |
| `multitenancy/TenantFilter.java` | Servlet filter — extracts `o.id` (Keycloak/Clerk) from JWT, resolves schema via `OrgSchemaMappingRepository`, binds `TENANT_ID` + `ORG_ID` ScopedValues |
| `multitenancy/SchemaBasedMultiTenantConnectionProvider.java` (class name approximate) | Hibernate `MultiTenantConnectionProvider<String>` — sets `search_path` to the tenant schema on connection checkout |
| `multitenancy/TenantIdentifierResolver.java` (approx) | Hibernate `CurrentTenantIdentifierResolver<String>` — reads `RequestScopes.TENANT_ID` |
| `multitenancy/OrgSchemaMapping.java` | Global-schema entity mapping Clerk org ID → schema name |
| `multitenancy/TenantScopedRunner.java` | Iterates all tenant schemas; used by all scheduled jobs |
| `multitenancy/TenantTransactionHelper.java` | Programmatic transaction helper for cross-schema provisioning |

### Auth Filters / Advice

| File | Purpose |
|---|---|
| `security/ApiKeyAuthFilter.java` | Validates `X-API-KEY` for `/internal/*` routes |
| `security/ClerkJwtAuthenticationConverter.java` (approx) | Converts JWT to `JwtAuthenticationToken`, extracts identity |
| `multitenancy/TenantFilter.java` | Binds tenant ScopedValues from JWT `o.id` |
| `member/MemberFilter.java` | Looks up `Member` by clerkUserId+schema, binds `MEMBER_ID`, `ORG_ROLE`, `CAPABILITIES` ScopedValues; triggers JIT member seeding |
| `security/PlatformAdminFilter.java` | Extracts `groups` JWT claim, binds `GROUPS` ScopedValue |
| `portal/CustomerAuthFilter.java` (approx) | Validates portal JWT, binds `CUSTOMER_ID`, `PORTAL_CONTACT_ID` ScopedValues |
| `security/JwtUtils.java` | Utility: extracts org ID from both Clerk v2 (`o.id`) and Keycloak JWT formats |

### Audit Emission Infrastructure

| File | Purpose |
|---|---|
| `audit/AuditService.java` | Interface: `log(AuditEventRecord)`, `findEvents(...)` |
| `audit/AuditEventBuilder.java` | Fluent builder for `AuditEventRecord`; used by every service that emits audit entries |
| `audit/AuditEvent.java` | `@Immutable` JPA entity; DB trigger prevents UPDATEs |
| `audit/AuditDeltaBuilder.java` | Utility for computing field-level diffs for audit `details` |

### Feature-Flag / Integration-Guard Resolution

| File | Purpose |
|---|---|
| `integration/IntegrationGuardService.java` | `requireEnabled(IntegrationDomain)` — reads `OrgSettings` flags for AI, ACCOUNTING, DOCUMENT_SIGNING; PAYMENT/EMAIL/KYC always pass |
| `integration/IntegrationDomain.java` | Enum: `ACCOUNTING, AI, DOCUMENT_SIGNING, EMAIL, KYC_VERIFICATION, PAYMENT` |
| `integration/IntegrationKeys.java` | Constants for secret-store keys per domain |
| `orgrole/RequiresCapability.java` | Annotation; resolved by AOP advice reading `RequestScopes.CAPABILITIES` |

### Integration-Port Adapter Resolution

| File | Purpose |
|---|---|
| `integration/OrgIntegration.java` | Entity: stores per-org integration config |
| `integration/secret/SecretStore.java` | Port interface: `storeSecret`, `getSecret`, `deleteSecret` |
| `integration/secret/EncryptedDatabaseSecretStore.java` | Impl: AES-GCM encryption, persisted in `org_integrations.config_json` |
| `s3/StorageService.java` | Port interface for file storage |
| `s3/S3StorageAdapter.java` | AWS S3 concrete implementation |
| `assistant/provider/LlmChatProviderRegistry.java` | Resolves LLM provider from `OrgIntegration` config |
| `packs/PackInstaller.java` | SPI: implemented by each pack type (field, compliance, template, clause, etc.) |
| `packs/PackCatalogService.java` | Aggregates all `PackInstaller` impls; resolves install state |
| `verticals/VerticalProfileRegistry.java` | Loads vertical profile JSON from classpath; used at provisioning |

---

## 7. Module Candidates (Bounded Contexts)

| # | Module Name | Java Packages | Responsibility | Type |
|---|---|---|---|---|
| 1 | **Tenancy & Provisioning** | `multitenancy`, `provisioning` | Schema-per-tenant isolation, Flyway DDL, org/schema mapping, ScopedValue carriers, JIT provisioning | Core-SaaS shared |
| 2 | **Identity & Access** | `security`, `member`, `orgrole`, `invitation` | JWT validation, filter chain, member sync, capability-based RBAC, org role management | Core-SaaS shared |
| 3 | **Platform Administration** | `accessrequest`, `billing` (subscription layer) | Admin-gated org sign-up OTP flow, platform-admin approval, tenant subscription lifecycle (PayFast) | Core-SaaS shared |
| 4 | **Projects** | `project`, `member` (ProjectMember), `capacity` | Project lifecycle (ACTIVE→COMPLETED→CLOSED→ARCHIVED), project member access control, resource allocation | Core-SaaS shared |
| 5 | **Tasks & Time** | `task`, `timeentry`, `expense` | Task lifecycle, recurrence, time logging, expense tracking, bulk time entry | Core-SaaS shared |
| 6 | **Customer Lifecycle** | `customer`, `compliance`, `checklist`, `prerequisite` | Customer onboarding/offboarding, dormancy, retention, compliance checklists, prerequisite gates | Core-SaaS shared |
| 7 | **Documents & Templates** | `document`, `template`, `s3` | Document upload/download, Tiptap + DOCX template rendering, generated document lifecycle | Core-SaaS shared |
| 8 | **Custom Fields & Views** | `fielddefinition`, `tag`, `view` | Custom field schemas, field groups, auto-apply, tags, saved views with server-side SQL filtering | Core-SaaS shared |
| 9 | **Invoicing & Billing** | `invoice`, `tax`, `billingrun`, `retainer` | Invoice lifecycle, tax calculation, retainer agreements, bulk billing runs | Core-SaaS shared |
| 10 | **Portal** | `portal` | Customer portal auth (magic links, JWTs), portal read-model, portal-facing REST surface | Core-SaaS shared |
| 11 | **Notifications & Activity** | `notification`, `comment`, `audit`, `event` | In-app notifications, email notifications, comments, append-only audit log, domain event bus | Core-SaaS shared |
| 12 | **Reporting & Export** | `reporting` | Report definitions, parameterised query execution, PDF/CSV export | Core-SaaS shared |
| 13 | **Automation Engine** | `automation` | Rule matching, condition evaluation, action executors (send email, create task, assign, AI, etc.), delayed actions, cron triggers | Core-SaaS shared |
| 14 | **Integrations & Pack System** | `integration`, `packs`, `seeder` | Integration port registry (storage, email, payment, AI, accounting), secret store, content pack lifecycle | Core-SaaS shared |
| 15 | **Proposals & Acceptance** | `proposal`, `acceptance` | Sales pipeline: proposal lifecycle, e-sign acceptance requests, certificate generation | Core-SaaS shared |
| 16 | **Information Requests** | `informationrequest` | Structured client data-collection workflow (request templates, items, portal upload flow) | Core-SaaS shared |
| 17 | **Capacity & Resource Planning** | `capacity` | Member weekly capacity, project allocations, utilization analytics | Core-SaaS shared |
| 18 | **AI Assistant** | `assistant` | BYOAK LLM integration, tool framework (read/write tools), SSE chat streaming | Core-SaaS shared |
| 19 | **Vertical Profile System** | `verticals`, `profile (reconciliation)` | Vertical profile JSON definitions, module registry, seeder orchestration for profile-specific content | Vertical-specific bootstrap |
| 20 | **Legal Vertical** | `verticals.legal.tariff`, `compliance.dataprotection` | LSSA tariff schedules, tariff-to-invoice-line integration, trust accounting (Phase 60), data protection (PAIA) | Vertical-specific |

**Notes:**
- Modules 1–18 are Core-SaaS and present in every tenant regardless of vertical.
- Modules 19–20 activate conditionally based on `OrgSettings.verticalProfile`.
- The accounting vertical (`integration/IntegrationDomain.ACCOUNTING`) is implemented via the integration port; no dedicated Java package beyond the Xero adapter (planned in Phase 71 at time of writing).
- `settings` (`OrgSettings`) is a cross-cutting configuration store referenced by virtually every module — it is not a bounded context in itself but a shared configuration aggregate.

---

## Essential Files Reference

- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/BackendApplication.java` — Entry point, `@EnableScheduling`
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java` — All ScopedValue holders
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java` — Tenant resolution filter
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` — Member/capability resolution filter
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java` — Multi-tenant job iterator
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` — Sealed event interface (all 35+ event types listed)
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListener.java` — Central event→automation dispatch
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — Flyway + seed orchestration
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java` — Vertical profile loader
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java` — Feature/integration flag enforcement
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogService.java` — Pack SPI aggregator
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java` — Capability-based authz annotation
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — Tenant configuration aggregate (20+ fields)
- `/backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/AssistantController.java` — SSE chat with ScopedValue re-bind pattern
- `/backend/CLAUDE.md` — Anti-patterns, test conventions, filter chain order

---

**Summary:** The Kazi backend has **27 direct subpackages** under `b2bstrawman`, expanding to roughly **40 distinct feature packages** with sub-packages. Entity count is approximately **55–60 JPA entities** (35 tenant-scoped, 5 in the public/global schema). The REST surface spans roughly **260–290 endpoints** across ~40 controllers. The most architecturally significant discovery is the **sealed `DomainEvent` bus** which wires all 20 modules together via Spring `ApplicationEventPublisher` — the automation engine subscribes to all events while notification, portal read-model sync, and audit are secondary consumers. The **`TenantScopedRunner` + `@Scheduled` pattern** is used uniformly across 10+ background jobs. The **vertical profile system** (JSON files + `VerticalProfileRegistry`) is an elegant mechanism for shipping vertical-specific seeding without conditional code in services.
