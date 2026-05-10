# Customer Lifecycle

**Bounded context:** see [`10-bounded-contexts.md` § customer-lifecycle](../10-bounded-contexts.md).

## Purpose

Owns the `Customer` aggregate, the `Customer ↔ Project` link, and the seven-state lifecycle machine that drives onboarding, dormancy, retention, offboarding, and DSAR/anonymisation. Sibling concerns kept out of this module: per-customer compliance checklists are owned by [`30-modules/checklists.md`](checklists.md); custom-field auto-apply is owned by [`30-modules/custom-fields-tags-views.md`](custom-fields-tags-views.md); the audit log itself lives in [`30-modules/audit.md`](audit.md). This module is what binds those siblings into a single end-to-end customer journey.

## Entities owned

- `Customer` — root aggregate; holds lifecycle status, type, custom fields, applied field-groups, and offboarded/anonymised stamps. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java:23`
- `LifecycleStatus` — enum + transition table (`PROSPECT → ONBOARDING → ACTIVE ⇄ DORMANT → OFFBOARDING → OFFBOARDED → {ACTIVE | ANONYMIZED}`). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/LifecycleStatus.java:8`
- `CustomerType` — `INDIVIDUAL | COMPANY | TRUST`. The `TRUST` value is a tax/legal entity classification and is unrelated to trust accounting. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerType.java:5`
- `CustomerProject` — many-to-many join entity for customer↔project linking, including `linkedBy` actor for audit. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProject.java:14`
- `DataSubjectRequest` — durable handle for an in-flight DSAR/PAIA request; status transitions audited. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataSubjectRequest.java`
- `ProcessingActivity` — register of personal-data processing activities (used by PAIA manual generation). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivity.java`
- `CustomerReadiness` — read-model record returned by the readiness endpoint (required-field status + checklist progress). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/setupstatus/CustomerReadiness.java`

The `Customer` row carries audit-grade lifecycle stamps (`lifecycleStatus`, `lifecycleStatusChangedAt`, `lifecycleStatusChangedBy`, `offboardedAt`) on the entity itself rather than in a separate event-sourced ledger; the audit log is authoritative for *who/when* and the entity holds the *current* projection. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java:70` (lifecycle status), `:79` (offboardedAt), `:186` (transition guard).

## REST surface

### `CustomerController` — 18 endpoints, base path `/api/customers`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java:56`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/customers` | List customers (saved-view + filter aware) `:92` |
| POST | `/api/customers` | Create — defaults to `PROSPECT` `:190` |
| GET | `/api/customers/{id}` | Detail `:182` |
| PUT | `/api/customers/{id}` | Update structural + custom fields `:224` |
| DELETE | `/api/customers/{id}` | Archive (soft, never hard-delete — ADR-062) `:256` |
| POST | `/api/customers/{id}/unarchive` | Reverse archive `:265` |
| POST | `/api/customers/{id}/transition` | Run a guarded lifecycle transition `:367` |
| GET | `/api/customers/{id}/lifecycle` | Lifecycle audit timeline `:378` |
| GET | `/api/customers/lifecycle-summary` | Aggregate counts per status `:164` |
| GET | `/api/customers/completeness-summary` | Per-customer readiness `:170` |
| GET | `/api/customers/completeness-summary/aggregated` | Roll-up for dashboard `:176` |
| POST | `/api/customers/{id}/projects/{projectId}` | Link to project `:276` |
| DELETE | `/api/customers/{id}/projects/{projectId}` | Unlink `:285` |
| GET | `/api/customers/{id}/projects` | List linked projects `:293` |
| GET | `/api/customers/{id}/unbilled-summary` | Unbilled-time roll-up `:300` |
| GET | `/api/customers/{id}/readiness` | `CustomerReadiness` (required fields + checklists) `:306` |
| GET | `/api/customers/{id}/fica-status` | FICA/KYC compliance flag `:317` |
| GET | `/api/customers/{id}/unbilled-time` | Detailed unbilled-time list `:322` |
| PUT | `/api/customers/{id}/field-groups` | Apply/unapply field groups `:331` |
| POST/GET | `/api/customers/{id}/tags` | Tag attach + list `:339`, `:349` |
| GET | `/api/customers/{id}/portal-contacts` | Portal contacts attached to this customer `:359` |
| POST | `/api/customers/dormancy-check` | On-demand dormancy sweep (mirrors scheduler) `:385` |

Endpoints exceed the 18 cited in A1 once tag/field-group sub-paths are counted; the canonical figure is the 18 lifecycle/profile endpoints above.

### `ProjectCustomerController` — 3 endpoints, base path `/api/projects/{projectId}/customers`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/ProjectCustomerController.java:19`

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/projects/{projectId}/customers` | Customers linked to a project `:27` |
| POST | `/api/projects/{projectId}/customers/{customerId}` | Link `:35` |
| DELETE | `/api/projects/{projectId}/customers/{customerId}` | Unlink `:47` |

### Compliance/data-protection sub-surface

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataRequestController.java:28` — `/api/data-requests` CRUD + status transitions + export staging + execute-deletion + deadline check (8 endpoints).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/AnonymizationController.java:16` — `POST /api/customers/{id}/anonymize`, `GET /api/customers/{id}/anonymize/preview`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportController.java:14` — `POST /api/customers/{id}/data-export`, `GET /api/data-exports/{id}`, `GET /api/data-exports`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataProtectionController.java:13` — `POST /api/settings/paia-manual/generate` (ZA-only).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/ProcessingActivityController.java:21` — `/api/settings/processing-activities` CRUD.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CompliancePackController.java:11` — `GET /api/compliance-packs/{packId}` (catalog read).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteController.java:13` — `GET /api/prerequisites/check` (transition gate inspection).

The `DataRequestController` is one of the legacy controllers flagged as a thin-controller-discipline violation in `backend/CLAUDE.md` (TD-009); new controllers added under this module must obey the one-service-call rule.

## Frontend pages / components

### Pages

- `/customers` — list with lifecycle-status filter, saved views, tags. `→ frontend/app/(app)/org/[slug]/customers/page.tsx`
- `/customers/[id]` — detail: profile, checklists, documents, unbilled time, KYC/FICA, data-protection tab. `→ frontend/app/(app)/org/[slug]/customers/[id]/page.tsx`
- `/compliance` — dashboard: lifecycle distribution, onboarding pipeline, in-flight data requests, dormancy check. `→ frontend/app/(app)/org/[slug]/compliance/page.tsx`
- `/compliance/requests` — DSAR queue. `→ frontend/app/(app)/org/[slug]/compliance/requests/page.tsx`
- `/compliance/requests/[id]` — DSAR detail. `→ frontend/app/(app)/org/[slug]/compliance/requests/[id]/page.tsx`
- `/settings/compliance` — retention policies, dormancy threshold, processing-activity register. `→ frontend/app/(app)/org/[slug]/settings/compliance/page.tsx`

### Types and components

- `Customer` type (id, name, email, lifecycleStatus, customerType, tags, customFields, promoted address/tax fields). `→ frontend/lib/types/customer.ts:18`
- `LifecycleStatus` (string union of the seven states — must stay in lockstep with the backend enum). `→ frontend/lib/types/customer.ts:7`
- `DataRequestResponse`, `DataRequestType`, `AnonymizationResult`, `RetentionPolicy`. `→ frontend/lib/types/customer.ts:202`
- Customer dialogs and badges (`CreateCustomerDialog`, `EditCustomerDialog`, `ArchiveCustomerDialog`, `CompletenessBadge`, `CustomFieldBadges`). `→ frontend/components/customers/`
- Compliance dashboard sections (`LifecycleDistributionSection`, `RetentionPolicyTable`, `CompliancePackList`). `→ frontend/components/compliance/`
- FICA types feed `/api/customers/{id}/fica-status`. `→ frontend/lib/types/fica.ts`

## Domain events

### Emitted

- `CustomerStatusChangedEvent` — published from `CustomerLifecycleService` on every transition (manual or scheduler-driven). Carries `customerId`, `oldStatus`, `newStatus`, `actorId`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/CustomerStatusChangedEvent.java:11` (record); `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java:58` (sealed-permit entry); emitted at `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleService.java:179` (interactive path) and `:289` (dormancy-scheduler path).

### Consumed

- The customer-lifecycle module does not subscribe to any other domain events. It calls into `prerequisite` and `compliance` services directly during transition handling, rather than reacting to events.

### Downstream subscribers (information only)

- `automation/TriggerConfigMatcher.java:70`/`:85` — the automation engine matches `CustomerStatusChangedEvent` against rule trigger configs (new-status / old-status filters); maps to `TriggerType.CUSTOMER_STATUS_CHANGED` (`automation/TriggerTypeMapping.java:45`).
- `notifications`, `customer-portal` read-model, and `audit` (in-tx) all see the event via the shared `DomainEvent` bus per [`20-cross-cutting/event-bus.md`](../20-cross-cutting/event-bus.md).

## Cross-cutting touchpoints

- **Multi-tenancy:** all customer reads/writes are tenant-scoped via `RequestScopes.TENANT_ID`; the dormancy scheduler iterates tenants via `TenantScopedRunner.forEachTenant` (the canonical scheduler pattern — A6 §7). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java:37`.
- **Dormancy scheduler:** `@Scheduled(cron = "0 0 2 * * *")` daily; identifies customers idle past `OrgSettings.dormancyThresholdDays` and triggers `ACTIVE → DORMANT`, emitting `CustomerStatusChangedEvent` per affected customer. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyScheduledJob.java:38`.
- **Audit:** every transition writes an audit row through `AuditService.log(...)` inside the same transaction — audit cannot lie about a rollback (A6 §3). Uses `AuditDeltaBuilder` for field-level diffs on update. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditDeltaBuilder.java:24`.
- **Prerequisite gates:** transitions are gated by `PrerequisiteService` — required custom fields and structural preconditions must hold or the transition raises `InvalidStateException`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java`.
- **Lifecycle guard:** `CustomerLifecycleGuard` is invoked by sibling services (e.g. invoicing, retainers) to refuse operations on `OFFBOARDED` / `ANONYMIZED` customers. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleGuard.java`.
- **Compliance packs auto-instantiate:** on customer create the `ChecklistInstantiationService` instantiates checklist templates whose `customerType` matches the new customer (e.g. FICA pack on legal-za + INDIVIDUAL/COMPANY/TRUST). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/ChecklistInstantiationService.java`.
- **Custom-field auto-apply:** the `appliedFieldGroups` JSON column tracks which `FieldGroup`s have been auto- or manually-applied (e.g. legal "FICA fields"). Owned by [`30-modules/custom-fields-tags-views.md`](custom-fields-tags-views.md); referenced here only as the seam.
- **DSAR / anonymisation:** the `datarequest/` package owns DSAR durability (`DataSubjectRequest`), export bundling (`DataExportService`), and the anonymisation sweep (`DataAnonymizationService`) which replaces PII with placeholders while retaining the row for audit linkage (ADR-062). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java`, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java`, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java`.
- **Retention clock:** `Customer.offboardedAt` is the start anchor for customer-level retention; `Project.retentionClockStartedAt` is the start anchor for matter/engagement-level retention (set on close, preserved on reopen — ADR-249). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:303`. The two clocks are independent — closing all of a customer's projects does not auto-offboard the customer.
- **PAIA manual:** generated by the data-protection package using ProcessingActivity records and JurisdictionDefaults; ZA tenants only — non-ZA tenants get a generic default-jurisdiction set. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/PaiaManualGenerationService.java`, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java`.
- **Capability gating:** mutating endpoints carry `@RequiresCapability` (per `backend/CLAUDE.md` § Authorization). DSAR-execute-deletion and anonymise are owner/admin-only.
- **Portal:** `PortalContact.customerId` FK depends on this module; portal magic-link issuance refuses anonymised customers. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContact.java:16`.

## Vertical specifics

- **Terminology overrides:** the canonical entity is `Customer`; the UI label is "Client" for `legal-za`, `accounting-za`, and `consulting-za` (`consulting-generic` keeps "Customer"). Maps live in `frontend/lib/terminology-map.ts:3` and `portal/lib/terminology-map.ts`. The backend never renames — code always says `Customer`.
- **Compliance packs:** vertical-specific compliance packs (e.g. legal-za FICA, accounting-za AML) ship as `COMPLIANCE` `PackType` units installed at provisioning by `CompliancePackSeeder`. On customer creation, `ChecklistInstantiationService` materialises one `ChecklistInstance` per matching template — `customerType` is the matching key, and `autoInstantiate` on the template is the on/off knob. Detail of which packs ship per vertical lives in [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md), not here.
- **Custom-field packs:** field groups are auto-applied by `customerType` and vertical. Mechanism is owned by `custom-fields-tags-views`; the per-vertical *list* of fields lives in [`60-verticals/legal-za.md`](../60-verticals/legal-za.md), [`60-verticals/accounting-za.md`](../60-verticals/accounting-za.md), and [`60-verticals/consulting-za.md`](../60-verticals/consulting-za.md).
- **Jurisdiction-aware retention/PAIA:** ZA jurisdiction is the only fully-implemented case (PAIA manual generation, retention defaults). Non-ZA tenants receive a fall-through default set. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java`.
- **Module gating:** there is no module-slug gate on the customer module itself — `Customer` is foundational across all profiles. Vertical-specific *attached* features (FICA fields, conflict-check, prescription tracking) are gated separately.

## Active ADRs

ADR statuses are the source of truth in [`90-adr-index.md`](../90-adr-index.md). All references below are Active as of 2026-05-10.

- **ADR-060** — `lifecycle-status-core-field`: lifecycle status is a first-class column on `Customer`, not a derived projection. Anchors the seven-state machine.
- **ADR-062** — `anonymization-over-hard-deletion`: never hard-delete an ACTIVE-or-later customer; transition through `OFFBOARDING → OFFBOARDED → ANONYMIZED`. Canonical privacy ADR.
- **ADR-066** — `computed-status-over-persisted`: where a status *can* be derived (e.g. dormancy from `lastActivityAt + thresholdDays`), prefer compute on read; the materialised `DORMANT` enum value is the documented exception, set only when the scheduler runs.
- **ADR-093** — `template-required-fields`: project/document templates can declare required customer fields; the `prerequisite` package enforces them at transition time.
- **ADR-094** — `conditional-field-visibility`: customer custom-field visibility/required-ness can depend on `customerType` and other field values.
- **ADR-193** — `anonymization-vs-deletion`: clarifies the boundary between anonymisation (default) and outright row deletion (forbidden once ACTIVE).
- **ADR-194** — `retention-policy-granularity`: retention is per-customer-type and per-jurisdiction, not org-wide.
- **ADR-195** — `dsar-deadline-calculation`: deadline math anchored to request received-at, not status-changed-at.
- **ADR-196** — `pre-anonymization-export-storage`: an export bundle must be staged and durable *before* the anonymise sweep runs.
- **ADR-197** — `calculated-vs-stored-deadlines`: DSAR deadlines are computed at read-time from anchor + jurisdiction policy, not stored.
- **ADR-199** — `filing-status-lazy-creation`: per-customer filing/processing-activity records materialise on first need, not at customer create.
- **ADR-249** — `retention-clock-starts-on-closure`: `Project.retentionClockStartedAt` is set on close and preserved on reopen — the canonical anchor is the *earliest* close. Customer-side mirror is `offboardedAt` (set on `OFFBOARDING → OFFBOARDED` per `Customer.java:196`).

ADR-014 (`plan-enforcement`) is **not** referenced — it is plan-tier-era and flagged Stale per `90-adr-index.md:438`.

## Key flows

- **Onboarding + KYC** — pointer to [`50-flows/customer-onboarding-and-kyc.md`](../50-flows/customer-onboarding-and-kyc.md) (PROSPECT → ONBOARDING → ACTIVE; FICA pack instantiation; portal contact issuance).
- **Dormancy detection** — daily `DormancyScheduledJob` at 02:00 iterates tenants via `TenantScopedRunner.forEachTenant`, identifies ACTIVE customers idle past `OrgSettings.dormancyThresholdDays`, transitions to `DORMANT`, emits `CustomerStatusChangedEvent` per change. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyScheduledJob.java:38`, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/CustomerLifecycleService.java:289`.
- **DSAR / anonymisation** — request received → status tracked on `DataSubjectRequest` → `DataExportService` stages the export bundle (ADR-196) → `DataAnonymizationService.anonymize(...)` replaces PII with placeholders → `Customer.lifecycleStatus = ANONYMIZED`. ADR-062 + ADR-193 + ADR-196.
- **Offboarding** — `ACTIVE | DORMANT → OFFBOARDING → OFFBOARDED`; transition to `OFFBOARDED` stamps `offboardedAt` (`Customer.java:196`) which begins the retention clock. From `OFFBOARDED` the customer may be reactivated to `ACTIVE` or progressed to `ANONYMIZED` once retention has elapsed.

## Open questions / known fragility

- **Seven-state enum is wide.** `PROSPECT, ONBOARDING, ACTIVE, DORMANT, OFFBOARDING, OFFBOARDED, ANONYMIZED` — the `OFFBOARDING → OFFBOARDED` split exists to give workflows a winding-down state but has no observed product use distinct from the terminal stamp. A consolidation pass (drop `OFFBOARDING`, fold into `OFFBOARDED` with a sub-status) would shrink the transition table from seven states to six. Not pursued; flagged here for future review.
- **Frontend/backend enum drift risk.** `LifecycleStatus` is duplicated as a Java enum (`backend/.../customer/LifecycleStatus.java:7`) and a TypeScript string union (`frontend/lib/types/customer.ts:6`); they currently match, but there is no compile-time link. A divergence (e.g. adding `SUSPENDED`) would not surface until a transition payload round-trips.
- **Two retention clocks, no auto-link.** `Customer.offboardedAt` and `Project.retentionClockStartedAt` are independent. Closing every project on a customer does not auto-offboard the customer; offboarding the customer does not close projects. This is deliberate (per ADR-249 the project clock is the load-bearing one for legal matter retention) but has caused operator confusion in past QA cycles — surfacing here as a known seam.
- **`CustomerType.TRUST` verbal clash.** The enum value `TRUST` is a tax/legal entity classification (a trust as a customer); it is unrelated to legal trust accounting. Glossary divergence #4. Any new feature naming must disambiguate explicitly.
- **DataRequestController violates thin-controller discipline.** Listed in `backend/CLAUDE.md` TD-009. New endpoints under `/api/data-requests` must follow the one-service-call rule even though existing siblings do not.
- **`PortalContact` entity name vs UI label.** The backend entity is `PortalContact`; some UI surfaces still say "Customer Contact". Glossary divergence #1; normalise to "Portal Contact" or "Contact" with disambiguation.
- **Reconciliation seeder is one-way.** Switching a tenant from `legal-za` back to `consulting-generic` does not uninstall legal compliance packs nor remove instantiated FICA checklists — orphaned rows remain (module-gated, so unreachable from UI but visible to anyone with DB access). Tracked in A6 §4 and `60-verticals/`.
