# Information Requests

**Bounded context:** see [`10-bounded-contexts.md` § information-requests](../10-bounded-contexts.md).

## Purpose

Templated client data-collection workflow. The firm publishes a structured ask ("upload your ID, your latest tax cert, and a paragraph describing the engagement"); the customer receives a portal link and submits each item; the firm member reviews, accepts or rejects per item; the request auto-completes when every required item is accepted. The workflow exists to replace ad-hoc email chasing with a tracked, reminder-driven cycle, and is the primary intake channel for KYC packs (legal-za FICA, accounting-za onboarding) and recurring data collection (annual audit, monthly bookkeeping). Sibling concerns kept out of this module: portal authentication is owned by [`30-modules/customer-portal.md`](customer-portal.md); uploaded files become `Document` rows owned by [`30-modules/documents-templates.md`](documents-templates.md); compliance checklists are a separate aggregate per [`30-modules/checklists.md`](checklists.md) (per ADR-134).

## Entities owned

- `RequestTemplate` — reusable blueprint, scoped per tenant, may be platform-seeded or custom; carries `name`, `description`, `source` (`PLATFORM | CUSTOM`), `packId` (links to seeder pack when source is PLATFORM), and `active` flag. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplate.java:16`, `:30` (source enum), `:33` (packId).
- `RequestTemplateItem` — line-item on a template (name, description, response type, required flag, sort order, file-type hints). Repository-only entity. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateItem.java`, repository at `→ informationrequest/RequestTemplateItemRepository.java`.
- `InformationRequest` — instance sent to a specific portal contact. Carries `requestNumber` (e.g. `REQ-0001`, generated via `RequestCounter`), `requestTemplateId` (nullable — ad-hoc requests are allowed), `customerId`, `projectId` (nullable), `portalContactId`, `status`, `reminderIntervalDays` (override on the org default), `dueDate`, `lastReminderSentAt`, plus lifecycle stamps (`sentAt`, `completedAt`, `cancelledAt`). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequest.java:22`, status field `:45`, `reminderIntervalDays` `:48`, lifecycle methods `:101–138`.
- `RequestItem` — per-data-point row anchored to an `InformationRequest`. Carries `templateItemId` (provenance, nullable when ad-hoc), `name`, `description`, `responseType` (`FILE_UPLOAD | TEXT_RESPONSE`), `required`, `fileTypeHints`, `sortOrder`, `status`, `documentId` (set when a file is submitted — FK to `documents`), `textResponse`, `rejectionReason`, `submittedAt`, `reviewedAt`, `reviewedBy`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestItem.java:21`, response/document fields `:42–66`.
- `RequestCounter` — per-tenant sequence row used by `RequestNumberService` to allocate human-readable request numbers atomically. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestCounter.java`, allocator at `→ informationrequest/RequestNumberService.java`.
- `RequestPackDefinition` — JSON record loaded from `classpath:request-packs/*/pack.json` by `RequestPackSeeder`; not a row, but the durable shape of a platform-seeded template. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestPackDefinition.java`, seeder `→ informationrequest/RequestPackSeeder.java`.

### Enums

- `RequestStatus` = `DRAFT | SENT | IN_PROGRESS | COMPLETED | CANCELLED`. `→ informationrequest/RequestStatus.java:4`. Glossary anchor at `glossary.md:143`.
- `ItemStatus` = `PENDING | SUBMITTED | ACCEPTED | REJECTED`. `→ informationrequest/ItemStatus.java:4`. Resubmission allowed from `PENDING` and `REJECTED` only (`RequestItem.SUBMITTABLE_STATUSES` at `:23`).
- `ResponseType` = `FILE_UPLOAD | TEXT_RESPONSE`. `→ informationrequest/ResponseType.java:3`.
- `TemplateSource` = `PLATFORM | CUSTOM`. `→ informationrequest/TemplateSource.java:3`.

## REST surface

### `RequestTemplateController` — base path `/api/request-templates`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/RequestTemplateController.java:23`. All mutating endpoints are `@RequiresCapability("CUSTOMER_MANAGEMENT")`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/request-templates` | List templates (filters by active/source) `:32` |
| GET | `/api/request-templates/{id}` | Detail (template + items) `:38` |
| POST | `/api/request-templates` | Create custom template `:43` |
| PUT | `/api/request-templates/{id}` | Update name/description/items `:52` |
| DELETE | `/api/request-templates/{id}` | Soft-delete (sets `active=false`; preserves audit trail) `:59` |
| POST | `/api/request-templates/{id}/duplicate` | Clone — used to fork a PLATFORM template into an editable CUSTOM copy `:66` |

### `InformationRequestController` — base path `/api/information-requests`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/informationrequest/InformationRequestController.java:24` (no class-level `@RequestMapping`; paths declared per method). All mutating endpoints are `@RequiresCapability("CUSTOMER_MANAGEMENT")`.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/information-requests` | Create draft (template-derived or ad-hoc) `:32` |
| GET | `/api/information-requests` | List (filterable by status/customer/project) `:41` |
| GET | `/api/information-requests/{id}` | Detail (request + items) `:49` |
| PUT | `/api/information-requests/{id}` | Update items/due-date/reminder cadence (DRAFT only — guarded by `requireEditable()`, `InformationRequest.java:144`) `:54` |
| POST | `/api/information-requests/{id}/send` | DRAFT → SENT; emits `InformationRequestSentEvent` `:61` |
| POST | `/api/information-requests/{id}/cancel` | Any non-COMPLETED state → CANCELLED; emits `InformationRequestCancelledEvent` `:67` |
| POST | `/api/information-requests/{id}/items` | Add an extra item to a draft `:73` |
| POST | `/api/information-requests/{id}/items/{itemId}/accept` | Firm reviewer accepts — may auto-complete the request `:80` |
| POST | `/api/information-requests/{id}/items/{itemId}/reject` | Firm reviewer rejects with reason; item returns to PENDING-for-resubmit `:87` |
| POST | `/api/information-requests/{id}/resend-notification` | Re-trigger the send email (e.g. after portal contact email change) `:96` |
| GET | `/api/customers/{customerId}/information-requests` | List per customer `:102` |
| GET | `/api/projects/{projectId}/information-requests` | List per project `:108` |
| GET | `/api/information-requests/summary` | Dashboard aggregation (open / overdue / completion) `:114` |

A1's "~12" total is the union of these two controllers (6 templates + 13 information-requests endpoints — A1 was conservative on the per-customer/per-project sub-paths).

### Portal sub-surface — `PortalInformationRequestController`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalInformationRequestController.java:28` — base path `/portal/requests`, portal-JWT authenticated (separate filter chain per `SecurityConfig.java:79`).

| Method | Path | Purpose |
|---|---|---|
| GET | `/portal/requests` | Customer-visible request list `:39` |
| GET | `/portal/requests/{id}` | Request detail with item cards `:51` |
| POST | `/portal/requests/{id}/items/{itemId}/upload` | Initiate upload — creates a `Document` row, returns presigned S3 URL (ADR-136) `:76` |
| POST | `/portal/requests/{id}/items/{itemId}/submit` | Mark item submitted (FILE: attaches the previously-created `documentId`; TEXT: stores `textResponse`); emits `RequestItemSubmittedEvent` `:93` |

The portal package layout has the controller in `customerbackend/`, not the firm-side `informationrequest/` package — A1 lists this as a layout quirk; the controller still binds the same `InformationRequestService` plus an upload-specific `PortalInformationRequestService` that owns the presign + document-create step (`PortalInformationRequestService.java:48` injects `StorageService`, `:152` calls `generateUploadUrl`, `:179` returns the presigned-URL DTO).

## Frontend pages / components

### Firm-side pages

- `/settings/request-templates` — list + create + edit + duplicate templates. Renders the firm's full library (PLATFORM + CUSTOM). `→ frontend/app/(app)/org/[slug]/settings/request-templates/page.tsx` (per `_discovery/A2-frontend-map.md:230`).
- `/settings/request-settings` — sets `OrgSettings.defaultRequestReminderDays` (the org-wide reminder cadence used as fallback when an individual request has no override). `→ frontend/app/(app)/org/[slug]/settings/request-settings/page.tsx` (per A2:232).
- `/customers/[id]` and `/projects/[id]` — embed the per-customer / per-project request lists fed by the convenience endpoints above; review-cycle UI (accept/reject) renders inline.

There is no firm-side `/information-requests` top-level page; the request is always reached via a customer or project context plus the dashboard summary widget surfaced on `/dashboard`.

### Portal pages

- `/requests` — list of in-flight requests for the authenticated portal contact. `→ portal/app/(authenticated)/requests/page.tsx:29` (`GET /portal/requests` per A3:116).
- `/requests/[id]` — per-request detail with one card per item, file picker, text input, submit button. `→ portal/app/(authenticated)/requests/[id]/page.tsx:63` (GET) `:138` (upload) `:153` (submit) per A3:117–119.
- The home dashboard surfaces an "Information Requests" card that reads from the same list endpoint.

## Domain events

All seven events permit on the sealed `DomainEvent` interface (`event/DomainEvent.java:51–57`).

### Emitted

- `InformationRequestDraftCreatedEvent` — fires when a request is created in DRAFT state via project-template instantiation. Used by automation triggers (e.g. "when KYC request drafted, route to admin reviewer"). Note: published from the project-templates module, NOT this module's service. `→ event/InformationRequestDraftCreatedEvent.java:11`, published `→ projecttemplate/ProjectTemplateService.java:1011`.
- `InformationRequestSentEvent` — DRAFT → SENT. The send-email listener subscribes to this; transactional phase is `AFTER_COMMIT` because email is irreversible (A6 §6). Published in two places: the standard `send` flow and a portal-side ad-hoc send. `→ event/InformationRequestSentEvent.java:11`, published `→ informationrequest/InformationRequestService.java:372` and `→ informationrequest/InformationRequestService.java:666`, listened at `→ informationrequest/InformationRequestEmailEventListener.java:53`.
- `InformationRequestCancelledEvent` — any non-COMPLETED state to CANCELLED. `→ event/InformationRequestCancelledEvent.java:11`, published `→ informationrequest/InformationRequestService.java:414`.
- `RequestItemAcceptedEvent` — firm reviewer accepted; consumed by the email listener to (optionally) confirm receipt to the client. `→ event/RequestItemAcceptedEvent.java:11`, published `→ informationrequest/InformationRequestService.java:468`, listened at `→ informationrequest/InformationRequestEmailEventListener.java:104`.
- `RequestItemRejectedEvent` — firm reviewer rejected with reason; the email listener pushes a rejection email so the client knows to resubmit. `→ event/RequestItemRejectedEvent.java:11`, published `→ informationrequest/InformationRequestService.java:522`, listened at `→ informationrequest/InformationRequestEmailEventListener.java:139`.
- `InformationRequestCompletedEvent` — auto-fires when the last required item is `ACCEPTED`. Drives the "all items received" portal email and the firm-side notification. `→ event/InformationRequestCompletedEvent.java:11`, published `→ informationrequest/InformationRequestService.java:572` (auto-completion path).
- `RequestItemSubmittedEvent` — portal submit. Drives the firm-side in-app notification ("client submitted"). Note: published from the portal-backend module, NOT this module's service. `→ event/RequestItemSubmittedEvent.java:11`, published `→ customerbackend/service/PortalInformationRequestService.java:349`.

### Consumed

The module does not subscribe to any external events. It calls into `PortalContactRepository`, `MagicLinkService`, `OrgSettingsRepository`, `StorageService`, and `DocumentRepository` directly during request processing rather than reacting to events.

### Downstream subscribers (information only)

- `automation/AutomationEventListener.java:25` — universal subscriber; the seven events above are matched against rule trigger configs.
- `notifications/NotificationService` — listens to `RequestItemSubmittedEvent` and `InformationRequestCompletedEvent` for in-app notifications (`InformationRequestNotificationEventListener.java:32`, `:48`, `:66` — all `AFTER_COMMIT`).
- `customer-portal` read-model — listens to `InformationRequestSentEvent` and item events to keep the portal-side projection in sync (per `10-bounded-contexts.md:329`).

## Cross-cutting touchpoints

- **Capability gate.** Every firm-side mutating endpoint is `@RequiresCapability("CUSTOMER_MANAGEMENT")` (controller `:33, :44, :53, :55, :60, :62, :67, :68, :74, :81, :88, :97`); read endpoints inherit the implicit member-bound check from `MemberFilter`. There is no separate `MANAGE_INFORMATION_REQUESTS` capability — owning the customer is the gate.
- **Multi-tenancy.** All entities are tenant-scoped (per A1 §319–321). The reminder scheduler iterates every tenant via `TenantScopedRunner.forEachTenant` — the canonical scheduler pattern from A6 §7. `→ informationrequest/RequestReminderScheduler.java:58`.
- **Reminder scheduler.** `@Scheduled(fixedRate=21_600_000)` — six-hour tick; processes any tenant whose request has `lastReminderSentAt + reminderIntervalDays` in the past, falling back to `OrgSettings.defaultRequestReminderDays` (default `5`) when the request has no override. Only `SENT` and `IN_PROGRESS` requests are eligible. `→ informationrequest/RequestReminderScheduler.java:54` (annotation), `:78` (status filter), `:87–95` (interval resolution + reference time selection). The scheduler updates `lastReminderSentAt` and writes an audit event per send.
- **Audit.** Every status transition writes `auditService.log(...)` in the same transaction (per A6 §3). Lifecycle mutations on `InformationRequest` are guarded by the entity itself (`InformationRequest.requireStatus(...)` `:241`) so audit and state stay aligned.
- **AFTER_COMMIT for outbound side-effects.** The email listener is `@TransactionalEventListener(phase = AFTER_COMMIT)` (`InformationRequestEmailEventListener.java:53, :103, :138, :174`). It runs in a fresh `REQUIRES_NEW` transaction (`:46–50`) because the AFTER_COMMIT callback fires while the outer transaction synchronisation is still active — joining it would silently drop magic-link writes.
- **Portal pre-auth seam.** Outbound emails carry a magic-link token issued by `MagicLinkService` so the recipient lands directly on `/requests/{id}` with a portal JWT minted on token exchange. The portal controller is on the `/portal/**` filter chain (separate from `/api/**`), portal-JWT authenticated, never gateway-routed (per `10-bounded-contexts.md:331`).
- **Document linkage.** A `FILE_UPLOAD`-typed item creates a real `Document` row at upload-init time (so the file is tracked + S3-keyed even before the customer submits). The `RequestItem.documentId` field is the FK; the document then surfaces in the customer's document list. `→ customerbackend/service/PortalInformationRequestService.java:84` (creates Document + presigned URL), `:189–222` (submit attaches `documentId`).
- **Org settings.** `OrgSettings.defaultRequestReminderDays` (Integer, default `5`) and `OrgSettings.requestPackStatus` (JSONB) are added to the OrgSettings aggregate by Phase 34. The pack-status field is the idempotency key for `RequestPackSeeder` so re-runs do not duplicate platform templates. `→ phase34-client-information-requests.md:202–203`.
- **Module gate.** `information_requests` is an enabled-module slug in three of four vertical profiles — present in `legal-za`, `accounting-za`, and `consulting-za`; absent from `consulting-generic`. `→ backend/src/main/resources/vertical-profiles/legal-za.json:5`, `accounting-za.json:3`, `consulting-za.json:7`. The slug is registered in `VerticalModuleRegistry.java:148` and the portal nav uses it to gate the `/requests` page (per A3:44).

## Vertical specifics

- **Pack-seeded templates per vertical.** `RequestPackSeeder` loads `classpath:request-packs/*/pack.json` at startup; the nine packs ship today as `annual-audit`, `company-registration`, `consulting-za-creative-brief`, `conveyancing-intake-za`, `fica-onboarding-pack`, `legal-za-liquidation-distribution`, `monthly-bookkeeping`, `tax-return`, `year-end-info-request-za` (`backend/src/main/resources/request-packs/`). Idempotency is by `(packId, version)` recorded in `OrgSettings.requestPackStatus` (`phase34-client-information-requests.md:507, :522`). Which packs install per vertical follows the standard pack-installer mechanism documented in [`30-modules/packs.md`](packs.md) and [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) — e.g. `fica-onboarding-pack` and `conveyancing-intake-za` ship with `legal-za`, `monthly-bookkeeping` and `annual-audit` with `accounting-za`, `consulting-za-creative-brief` with `consulting-za`.
- **Module gate, not terminology overlay.** "Information Request" is not renamed across verticals; it is the same label everywhere the feature is enabled. The verticality is "is the feature on?" not "what is it called?" — contrast with Customer/Client/Matter overlays in `customer-lifecycle`.
- **`consulting-generic` is the cold case.** A `consulting-generic` tenant does not have `information_requests` in its `enabledModules`, so the firm-side settings page is gated, the portal page redirects, and no platform packs install. The mechanism is the same nine-layer module guard pattern as trust-accounting, just three layers deep instead of nine.

## Active ADRs

ADR statuses are the source of truth in [`90-adr-index.md`](../90-adr-index.md). All references below are Active as of 2026-05-10.

- **ADR-134** — `dedicated-entity-vs-checklist-extension`: `InformationRequest` is a first-class aggregate, not a synthetic checklist instance. Different audience (client vs firm), different lifecycle (multi-step review cycle vs binary tick), different portal surface. Indexed under "Project, task, time, budget" in `90-adr-index.md:146`; phase-doc anchor `phase34-client-information-requests.md:1738`.
- **ADR-135** — `reminder-strategy`: interval-based reminders (every N days from `lastReminderSentAt + reminderIntervalDays`), not escalating-cadence or per-item nudges. Per-request override on top of an org default. Indexed under "Comments & notifications" in `90-adr-index.md:230`; phase-doc anchor `phase34-client-information-requests.md:1739`.
- **ADR-136** — `portal-upload-flow`: client uploads via presigned S3 URL — same pattern as the rest of `documents-templates`. The information-requests module reuses `StorageService.generateUploadUrl(...)` rather than introducing a custom path. Indexed under "Portal" in `90-adr-index.md:279`; phase-doc anchor `phase34-client-information-requests.md:1740`.
- **ADR-137** — `project-template-integration-scope`: when a project template instantiates a new project that has an attached request template, the request is created in `DRAFT` (not auto-sent). The firm reviewer reviews and explicitly sends — preserving customisation per engagement. Indexed under "Project, task, time, budget" in `90-adr-index.md:147`; phase-doc anchor `phase34-client-information-requests.md:1741`.

## Key flows

- **KYC / onboarding intake.** See [`50-flows/customer-onboarding-and-kyc.md`](../50-flows/customer-onboarding-and-kyc.md) for the load-bearing flow: PROSPECT customer → portal contact issued → `fica-onboarding-pack` request drafted → SENT (magic-link email) → portal upload + submit → firm review → COMPLETED → unblocks `ONBOARDING → ACTIVE` transition gate.
- **Send / submit / review cycle.** Firm creates DRAFT (template or ad-hoc) → `send` transition emits `InformationRequestSentEvent` → AFTER_COMMIT email listener fires magic-link email → portal upload-init creates `Document` + returns presigned URL → portal submit attaches `documentId` (or `textResponse`), emits `RequestItemSubmittedEvent` → firm member accepts/rejects per item → on last `ACCEPTED` of a `required` item, `InformationRequestCompletedEvent` fires.
- **Reminder loop.** Six-hourly scheduler tick → for each tenant, find SENT/IN_PROGRESS requests where `now - referenceTime > intervalDays` (referenceTime = `lastReminderSentAt ?? sentAt`) → send reminder email → stamp `lastReminderSentAt` → audit event.

## Open questions / known fragility

- **Item-level vs request-level acceptance.** Today, rejecting an item flips it back to `PENDING`-equivalent (via `RequestItem.SUBMITTABLE_STATUSES` at `RequestItem.java:23`) so the client can resubmit, but there is no "request-level reject" — a request stays open until either every required item is `ACCEPTED` (auto-completion at `InformationRequestService.java:572`) or a firm member explicitly cancels it. Two open product questions: (a) does a single rejected item block auto-completion forever (it does, today) or should there be an "accept with caveat" override; (b) should there be a request-level "send reminders only on rejected items" mode, or is the global six-hour cadence sufficient. Tracked here for future review; no architectural blocker.
- **Document linkage seam.** Files uploaded through the portal land in the `documents` module (`RequestItem.documentId` FK to the `documents` table). They show up in the customer's document list immediately after upload-init — even before the client clicks "submit" — because the `Document` row is created at presign time (`PortalInformationRequestService.java:84` precedes `submit`). This is technically a "ghost" document if the client abandons the upload mid-flow; there is no janitor today to garbage-collect orphaned documents whose `RequestItem.status` is still `PENDING`. Possible source of confusion in customer support. Reaper-job pattern (per ADR-271) would fit if this becomes operationally annoying.
- **Reminder cadence vs portal-contact email deliverability.** The reminder loop only checks `lastReminderSentAt + intervalDays`; it does not back off when the portal contact's email has been bouncing. If a contact's email deliverability is degraded (rate-limited, hard-bounce), Kazi will keep emailing every `intervalDays` until the request is completed or cancelled. The email module's rate-limit (ADR-160) prevents hot-loop floods at the channel layer, but does not surface back to the request module. Intersection with `notifications` module rate-limit policy is not currently codified.
- **Portal-controller package quirk.** `PortalInformationRequestController` lives in `customerbackend/controller/`, not in the firm-side `informationrequest/` package. The split is a Phase 34 historical accident (the portal-facing controller was scaffolded alongside other portal surfaces); it has no functional impact but means a grep for "all info-request code in one place" misses the portal endpoints. Cosmetic.
- **No `MANAGE_INFORMATION_REQUESTS` capability.** All firm-side mutations gate on `CUSTOMER_MANAGEMENT`, which means anyone who can manage customers can also send/cancel/review requests. No role today wants the split, but if a vertical needs "can review submissions but not edit customers" the capability would have to be added (see `identity-access` for the contract).
- **Soft-delete via `active=false`.** `RequestTemplateController DELETE` flips `active`, never row-removes (`RequestTemplate.deactivate()` at `:61`). This is the standard soft-delete pattern but is undocumented in the API — clients must check `active` to know if the template is still usable. Worth surfacing in the OpenAPI when QA writes the contract test.
