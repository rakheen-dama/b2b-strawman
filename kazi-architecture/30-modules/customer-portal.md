# Customer Portal (Backend)

**Bounded context:** see [`10-bounded-contexts.md` § customer-portal](../10-bounded-contexts.md).
**Companion page:** the `portal/` Next.js app itself is documented in `70-repos/portal.md`. This page covers the BACKEND side — auth, controllers, the read-model, and the events that feed it.

## Purpose

Three responsibilities, one bounded context:

1. **Magic-link authentication for end customers.** External clients of a tenant org sign in with an emailed one-time token, exchange it for a backend-minted **Portal JWT**, and authenticate every subsequent call with `Authorization: Bearer <jwt>` `→ ../_discovery/A6-cross-cutting.md:81-86`. ADR-030 establishes the database-backed `MagicLinkToken` model (over the Phase 4 Caffeine MVP); ADR-T005 explains the strategic choice (magic link vs Keycloak customer accounts vs shared PIN); ADR-077 explains JWT-in-localStorage (vs HttpOnly cookie / BFF).
2. **Portal-facing REST surface.** `/portal/**` (post-auth) and `/api/portal/**` (pre-auth, token-gated acceptance) — a separate filter chain (`SecurityConfig.java:79`, `@Order(1)`, `securityMatcher("/portal/**")`) `→ ../_discovery/A6-cross-cutting.md:94` that runs only `customerAuthFilter`. Portal traffic **bypasses the gateway entirely** `→ ../_discovery/A3-portal-gateway-map.md:241-259`; the portal app at port 3002 hits the Spring Boot backend at port 8080 directly.
3. **Portal read-model.** A separate set of denormalized tables, populated by `@TransactionalEventListener(AFTER_COMMIT)` handlers in `customerbackend/handler/PortalEventHandler.java` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java:127`. The read-model is a **security boundary**, not just a perf optimisation: portal queries never touch the staff-side write tables, so a portal bug cannot leak draft invoices, internal comments, or private task fields (ADR-078, ADR-031).

The hard line is **trust-boundary separation from the staff frontend.** Portal and staff have separate auth filter chains, separate JWT shapes, separate session storage, and a separate read schema. There is zero shared traffic path. The matrix per A3 §11 `→ ../_discovery/A3-portal-gateway-map.md:251-258`:

| Dimension | Gateway + Staff Frontend | Portal |
|---|---|---|
| Auth mechanism | Keycloak OIDC + OAuth2 session cookie | Magic link + portal JWT in localStorage |
| Session storage | PostgreSQL / Redis (server-side) | localStorage (client-side, 1h TTL) |
| Token type | OAuth2 access token (relay to backend) | Backend-minted HS256 JWT (`PortalJwtService`) |
| Entry point | `/login/oauth2/code/keycloak` | `POST /portal/auth/request-link` + `/exchange` |
| Backend path prefix | `/api/**` (proxied by gateway) | `/portal/**`, `/api/portal/**` (direct) |

What this module **does not** own: the trust ledger, retainer agreements, projects, invoices, proposals, information-requests, deadlines — every one of those is its own sibling module that emits domain events; this module's read-model **listens** to them and projects a portal-safe view. See §5 (subscribers).

## Entities owned

### `PortalContact` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContact.java:16`

Table `portal_contacts` (tenant schema) `→ ../_discovery/A1-backend-map.md:264`. A person on the customer side authorised to use the portal.

- `orgId` (Clerk org id, length 255), `customerId` FK→`customers`.
- `email` (NOT NULL), `displayName`.
- `role` `ContactRole` enum: `PRIMARY, BILLING, GENERAL` `→ portal/PortalContact.java:18-22`.
- `status` `ContactStatus` enum: `ACTIVE, SUSPENDED, ARCHIVED` `→ portal/PortalContact.java:24-28`.
- Auto-provisioned by `PortalContactAutoProvisioner` `→ portal/PortalContactAutoProvisioner.java:42` when an outbound flow names a portal email — **`@EventListener` (no transactional phase)** because creation must happen inside the source transaction so downstream sends (proposal email, info-request email) can attach to the same row.

Glossary canonical entry `→ ../glossary.md:205`. UI sometimes says "Customer Contact"; backend always says PortalContact (Divergence #1, glossary §332).

### `MagicLinkToken` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkToken.java:19`

Table `magic_link_tokens` (tenant schema) `→ ../_discovery/A1-backend-map.md:265`. Database-backed one-time token (ADR-030).

- `portalContactId` FK→`portal_contacts`.
- `tokenHash` (SHA-256, length 64) — **the raw token is returned to the client and never stored** `→ portal/MagicLinkToken.java:13-16`.
- `expiresAt`, `usedAt` (single-use enforcement), `createdAt`, `createdIp` (audit trail).
- `markUsed()` flips `usedAt` `→ portal/MagicLinkToken.java:55-57` — idempotent single-use.
- `isExpired()` checks `Instant.now() > expiresAt` `→ portal/MagicLinkToken.java:59`.

The lookup-by-hash design (no tenant column on the row) means the portal can resolve a token without first knowing the tenant — the email comes in with `?orgId=...`, but the hash check runs across the magic-link table per ADR-030 rationale.

Glossary `→ ../glossary.md:166`.

## REST surface

Two prefix families, both validated by the dedicated portal filter chain (`SecurityConfig.java:79`, `securityMatcher("/portal/**")`).

### Pre-auth (no JWT required)

| Verb + path | Controller | Notes |
|---|---|---|
| `POST /portal/auth/request-link` | `PortalAuthController` `→ portal/PortalAuthController.java:28-35` | Body `{ email, orgId }`. Returns `{ message, magicLink? }` — `magicLink` is populated in dev mode only `→ ../_discovery/A3-portal-gateway-map.md:66`. Client IP captured for audit (`HttpServletRequest.getRemoteAddr()`). |
| `POST /portal/auth/exchange` | `PortalAuthController` `→ portal/PortalAuthController.java:37-43` | Body `{ token, orgId }`. Returns `{ token, customerId, customerName }` — the `token` field is the portal JWT (HS256, 1h TTL per ADR-077). |
| `GET /portal/branding?orgId=` | `PortalBrandingController` `→ portal/PortalBrandingController.java` | Pre-auth org branding (logo, colour, name) for the login page. ADR-079: org id arrives via query param so the login screen can render firm-branded login before the user has any token. |
| `GET /api/portal/acceptance/{token}` | `PortalAcceptanceController` (in `acceptance/`) | Token-gated read of an `AcceptanceRequest` for the public acceptance page `→ ../_discovery/A3-portal-gateway-map.md:91`. |
| `POST /api/portal/acceptance/{token}/accept` | `PortalAcceptanceController` | Customer accepts the document by typed name. Hybrid read-model boundary applies (ADR-109): the **acceptance action reads live**, not from the read-model, to avoid accepting a revoked request. |
| `GET /api/portal/acceptance/{token}/pdf` | `PortalAcceptanceController` | Iframe-srcable PDF stream `→ ../_discovery/A3-portal-gateway-map.md:93`. |

The two prefix shapes (`/portal/auth/*` vs `/api/portal/acceptance/*`) are not arbitrary — the `/api/portal/acceptance/*` family predates the rest of the portal surface and is explicitly token-gated (no portal session), so it sits under `/api/...` to make pre-auth status visible in the path.

### Post-auth (Bearer portal JWT)

`CustomerAuthFilter.java` validates the JWT, calls `PortalJwtService` to verify HS256 + claims, and binds `RequestScopes.CUSTOMER_ID` + `RequestScopes.PORTAL_CONTACT_ID` ScopedValues `→ ../_discovery/A1-backend-map.md:527`. Subsequent service queries scope by these values.

| Verb + path | Controller | Notes |
|---|---|---|
| `GET /portal/session/context` | `PortalContextController` | Returns `tenantProfile, enabledModules, terminologyKey, brandColor, orgName, logoUrl` `→ ../_discovery/A3-portal-gateway-map.md:131`. The portal app calls this on mount to drive `TerminologyProvider` + nav-item gating. |
| `GET /portal/me` | `PortalContextController` (or sibling) | Read-only contact profile (name, email, role, customer). |
| `GET /portal/projects`, `/{id}`, `/{id}/tasks`, `/{id}/documents`, `/{id}/comments`, `/{id}/summary` | `PortalProjectController` `→ portal/PortalProjectController.java`; project-detail subpaths served via the read-model | Five parallel reads on the project detail page `→ ../_discovery/A3-portal-gateway-map.md:39`. All hit `portal_projects` / `portal_documents` / `portal_comments` / `portal_project_summaries` / `portal_tasks`. |
| `POST /portal/projects/{id}/comments` | `PortalCommentController` `→ customerbackend/controller/PortalCommentController.java` | Single write surface from the portal (besides upload + accept). Comment entity is created in the staff write-tables; read-model picks it up via `CommentCreatedEvent`. |
| `GET /portal/invoices`, `/{id}`, `/{id}/download`, `/{id}/payment-status` | `PortalInvoiceController` `→ customerbackend/controller/PortalInvoiceController.java` | **Visibility is enforced at sync time, not read time** (ADR-078): only invoices in `SENT, PAID` are projected into `portal_invoices`. A portal bug cannot leak a draft. |
| `GET /portal/api/proposals`, `/{id}`; `POST /{id}/accept`, `/{id}/decline` | `PortalProposalController` (in `proposal/`) | Note the prefix `/portal/api/proposals` (vs `/portal/proposals`) — historic naming carried forward. |
| `GET /portal/requests`, `/{id}`; `POST /{id}/items/{itemId}/upload`, `/{id}/items/{itemId}/submit` | `PortalInformationRequestController` `→ customerbackend/controller/PortalInformationRequestController.java` | Module-gated `information_requests`. The two POSTs are the data-collection write surface. |
| `GET /portal/acceptance-requests/pending` | `PortalAcceptanceRequestController` `→ customerbackend/controller/PortalAcceptanceRequestController.java` | **List view** — read from read-model (ADR-109 hybrid). Single-row view + accept use the live path. |
| `GET /portal/deadlines`, `/{sourceEntity}/{id}` | `PortalDeadlineController` `→ customerbackend/controller/PortalDeadlineController.java` | Module `regulatory_deadlines`; profiles `accounting-za, legal-za`. ADR-256 polymorphic-portal-deadline-view. |
| `GET /portal/trust/summary`, `/movements?limit=`, `/matters/{matterId}/transactions`, `/matters/{matterId}/statement-documents` | `PortalTrustController` `→ customerbackend/controller/PortalTrustController.java` | Module `trust_accounting`; profile `legal-za`. **Module-gated returns 404 (not 403)** — the module's existence is hidden from the portal `→ ../_discovery/A6-cross-cutting.md:245`. |
| `GET /portal/retainers`, `/{id}/consumption` | `PortalRetainerController` `→ customerbackend/controller/PortalRetainerController.java` | Module `retainer_agreements`; profiles `legal-za, consulting-za`. |
| `GET /portal/activity?tab=MINE\|FIRM` | `PortalActivityController` `→ portal/PortalActivityController.java` | Cross-entity timeline. `PortalActivityEventTypes` enumerates the visible event set. |
| `GET /portal/branding` (post-auth variant if any) / settings overlap | `PortalBrandingController` | Same controller as pre-auth. |
| Notification preferences | `PortalNotificationPreferenceController` `→ portal/notification/PortalNotificationPreferenceController.java` | Phase 68 — per-contact email digest opt-in. |
| `POST /internal/portal/resync/...` | `PortalResyncController` `→ customerbackend/controller/PortalResyncController.java` | Internal-only DR rebuild path — full refresh of the read-model from authoritative state. |

A1's mapping `→ ../_discovery/A1-backend-map.md:419-430` lists nine portal-facing controllers; the actual count once `customerbackend/` is included is closer to 13 across the two packages. The split between `portal/` (auth + branding + activity) and `customerbackend/` (read-model controllers) is historical: `customerbackend/` is the dedicated read-model layer per ADR-031/078/253; `portal/` holds the auth + filter primitives.

## Frontend pages / components

This is the **backend** module page. The portal app's 18 pages are enumerated in `70-repos/portal.md` (and the route map in A3 §2 `→ ../_discovery/A3-portal-gateway-map.md:30-54`). Only the **staff-side** admin surfaces that touch portal concerns belong here:

- **`/org/[slug]/settings/general/page.tsx`** — portal branding form (logo upload, brand colour, org name override) `→ ../_discovery/A2-frontend-map.md` (settings family). Backend writes feed `PortalBrandingController` reads.
- **`/org/[slug]/customers/[id]/page.tsx`** — customer detail with a **Portal Contacts tab** for CRUDing `PortalContact` rows on a customer. This is where staff add/suspend/archive a contact's portal access; magic-link tokens are revoked indirectly when status flips to `SUSPENDED` / `ARCHIVED` (see §6 token-revocation gap).
- **`/org/[slug]/settings/notifications/portal-notifications/...`** — opt-in management for `PortalNotificationPreference` (Phase 68).

## Domain events

### Published

The portal owns no public domain events of its own. `PortalContactAutoProvisioner` writes a `PortalContact` row in-flight (`@EventListener`, not transactional) but does not publish an event for downstream listeners — its observers read the row directly.

`PortalEventHandler` (in `customerbackend/`) **defines** several internal events used to keep the read-model coherent — `PortalTaskCreatedEvent`, `PortalTaskUpdatedEvent`, `PortalTaskDeletedEvent`, `ProjectUpdatedEvent`, `CustomerProjectUnlinkedEvent`, `DocumentVisibilityChangedEvent`, etc. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/event/`. These are **internal to the read-model machinery**, not part of the cross-module event vocabulary in `20-cross-cutting/domain-events.md`.

### Subscribed (the read-model is event-driven)

`PortalEventHandler.java` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/handler/PortalEventHandler.java` registers ~20 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` handlers (one per source event), each projecting into a portal table:

| Source event | Source module | Read-model write | Anchor |
|---|---|---|---|
| `DocumentGeneratedEvent`, `DocumentUploadedEvent`, `DocumentVisibilityChangedEvent` | documents-templates | `portal_documents` | `→ A1-backend-map.md:476` |
| `InvoiceSentEvent`, `InvoicePaidEvent`, `InvoiceVoidedEvent` | invoicing | `portal_invoices` (status filter at sync — only SENT/PAID) | ADR-078 |
| `AcceptanceRequestSentEvent`, `AcceptanceRequestAcceptedEvent`, `AcceptanceRequestRevokedEvent` | proposals-acceptance | `portal_acceptance_views` (list view only — single-row + accept go live per ADR-109) | ADR-109 |
| `ProposalSentEvent`, `ProposalAcceptedEvent`, `ProposalDeclinedEvent`, `ProposalExpiredEvent` | proposals-acceptance | `portal_proposals` | A1 §`portal listeners` |
| `InformationRequestSentEvent`, `InformationRequestSubmittedEvent` | information-requests | `portal_requests`, `portal_request_items` | A1 §`portal listeners` |
| Project/Task create/update/delete events | projects, tasks | `portal_projects`, `portal_tasks`, `portal_project_summaries` | Phase 7 base |
| `CommentCreatedEvent` (portal-visible only) | comment | `portal_comments` (filtered by `visibility = PORTAL`) | comment-visibility ADR-037 |
| Trust transaction events | trust-accounting | `portal_trust_balance`, `portal_trust_transactions` (with `PortalTrustDescriptionSanitiser` per ADR-254) | ADR-253, ADR-254 |
| Retainer period events | retainers | `portal_retainer_summaries`, `portal_retainer_consumption_entries` (member-display rule per ADR-255) | ADR-253, ADR-255 |
| Deadline events | (legal/accounting verticals) | `portal_deadline_views` (polymorphic per ADR-256) | ADR-256 |

The phase choice is not optional. Email and read-model writes both sit on `AFTER_COMMIT` for the same reason: a rolled-back source transaction must not leak data outwards `→ ../_discovery/A6-cross-cutting.md:336-337`. Audit, by contrast, sits **inside** the source transaction so a rolled-back action also rolls back its audit trail (`10-bounded-contexts.md:451`).

### Email channel

`PortalEmailNotificationChannel.java` `→ portal/notification/PortalEmailNotificationChannel.java:115, :152, :182, :209` is a **separate** email path with four `@TransactionalEventListener(AFTER_COMMIT)` handlers — acceptance, proposal, document, invoice. Phase 68 also adds three new portal-only events (trust activity, deadline approaching, retainer period closed). ADR-258 ("portal notification — no double-send rule") locks in the principle: new portal events flow through the new channel; existing events keep their existing wiring, so no client gets two emails for the same transaction.

## Cross-cutting touchpoints

### Magic-link generation, hashing, expiry (ADR-030)

`MagicLinkService.java:130` `→ ../_discovery/A6-cross-cutting.md:83` issues a 32-byte random token, stores SHA-256 hash + `expiresAt` (typical TTL ~15 min, single-use), emails the raw token to the contact's email, and never persists the raw value. `PortalAuthService.exchangeToken(token, orgId)` looks up by hash, validates `usedAt IS NULL AND expiresAt > now()`, marks `usedAt`, and mints the portal JWT. `MagicLinkCleanupService.java:34` `→ ../_discovery/A6-cross-cutting.md:379` runs hourly to delete tokens past `expiresAt` — a small batch job that keeps the table bounded.

### Portal JWT (ADR-077)

Backend-minted HS256, 1h TTL. Carries `org_id`, `customer_id`, `portal_contact_id`, `email`. Stored in `localStorage` under key `portal_jwt` `→ ../glossary.md:207`. `CustomerAuthFilter` validates on every `/portal/**` request and binds the ScopedValues; on 401 the portal app clears storage and hard-navigates to `/login` `→ ../_discovery/A3-portal-gateway-map.md:75`. The XSS-vs-XSRF tradeoff is documented in ADR-077: localStorage chosen because (a) the portal is read-mostly except comment-post + acceptance + info-request submit, (b) 1h TTL caps blast radius, (c) avoiding cookies sidesteps the BFF infrastructure that ADR-076 rejected.

### Portal traffic does NOT use the gateway (ADR-076)

The portal hits the backend at `NEXT_PUBLIC_PORTAL_API_URL` (default `http://localhost:8080`) directly — no gateway hop `→ ../_discovery/A3-portal-gateway-map.md:243-247`. The gateway only handles the staff frontend's OAuth2 session relay. This is the load-bearing trust-boundary separation: a misconfigured `TokenRelay=` filter could not, even in principle, leak a portal JWT into the staff request flow because they never share a transit path.

### CustomerAuthFilter binds ScopedValues

`portal/CustomerAuthFilter.java` validates the portal JWT and binds **`RequestScopes.CUSTOMER_ID` + `RequestScopes.PORTAL_CONTACT_ID`** (in addition to `TENANT_ID`) `→ ../_discovery/A1-backend-map.md:527`, `→ ../_discovery/A6-cross-cutting.md:27-29`. Downstream services use these to scope queries — e.g., `PortalQueryService.findProjectsForContact()` filters by `CUSTOMER_ID`. Direct `ScopedValue.where()` outside `RequestScopes.bindTenantScope(...)` is discouraged `→ ../_discovery/A6-cross-cutting.md:29`; the filter follows the carrier-chain pattern.

### Read-model boundary (ADR-031, ADR-078, ADR-253)

The read-model is a **security boundary**. Three rules:
- **Visibility filtering happens at write time.** Draft invoices never enter `portal_invoices`; internal comments never enter `portal_comments`; un-published proposals never enter `portal_proposals`. A portal endpoint cannot leak something the read-model doesn't have.
- **Minimal projection.** Tasks sync only `name, status, assigneeName` — descriptions, billable flags, time estimates never enter the portal `→ ADR-078 §rationale`.
- **Sanitisers are last-mile.** `PortalTrustDescriptionSanitiser` `→ customerbackend/service/PortalTrustDescriptionSanitiser.java` strips matter-internal narrative from trust-transaction descriptions before sync (ADR-254).

ADR-253 reinforces: portal vertical surfaces (trust balance, retainer hour-bank, deadlines) are **read-model extensions**, not new entities. Phase 68 added six raw SQL tables to the `portal` schema (global Flyway V19) and per-entity sync services (`TrustLedgerPortalSyncService`, `RetainerPortalSyncService`, `DeadlinePortalSyncService`), all `JdbcClient`-based against `@Qualifier("portalJdbcClient")`. JPA was rejected because multi-`SessionFactory` Hibernate entanglement was specifically eliminated in Phase 13.

### Branding-per-tenant

Portal logo, colour, and name are stored on the org settings entity and exposed via `PortalBrandingController`. The portal login page reads `GET /portal/branding?orgId=...` pre-auth (ADR-079: orgId comes via the magic-link query param) and threads brand state through `useBranding()`. Post-auth, `PortalSessionContext` carries the same fields so authenticated pages render branded chrome without a second round-trip.

## Vertical specifics

The portal mirrors the staff frontend's vertical-aware UI but ships its own copies of the gating data:

- **Terminology mirror** — `portal/lib/terminology-map.ts:1` is **duplicated** from `frontend/lib/terminology-map.ts` because the portal is a separate Next.js bundle `→ ../_discovery/A6-cross-cutting.md:218`. Renaming a profile key requires changes in **both** map files. Backend exposes only the `terminologyKey` (the resolved map lives in the frontend bundles).
- **Nav-item gating** — `portal/lib/nav-items.ts:32-99` declares `profiles?: string[]` and `modules?: string[]` per nav entry. `filterNavItems()` hides inapplicable items before render. Trust, Retainer, and Deadlines are profile-gated; Information Requests + Document Acceptance are module-gated `→ ../_discovery/A3-portal-gateway-map.md:151-159`.
- **Page-level module gate** — pages like `trust/page.tsx` and `deadlines/page.tsx` re-check `ctx.enabledModules` and `router.replace("/home")` if the module is off. **The backend is the source of truth** — endpoints return 404 (not 403) when the module is disabled `→ ../_discovery/A6-cross-cutting.md:245`.
- **Trust-accounting's nine-layer defence** lists portal contributions at layers 7–8: portal nav gate (profile + module) and portal page redirect `→ ../_discovery/A6-cross-cutting.md:243-244`. The portal is one of the nine layers; it is not the load-bearing one (that is the backend service guard + the export hard-guard ADR-276).

## Active ADRs

**Approach + auth:**
- **ADR-020** — customer-portal-approach (conceptual; chose magic-link over Clerk customer accounts).
- **ADR-T005** — magic-links-over-customer-accounts (template-level rationale).
- **ADR-030** — magic-link-auth-for-customers (DB-backed `MagicLinkToken` over the Phase 4 Caffeine MVP).
- **ADR-077** — portal-jwt-storage (localStorage, 1h TTL, accepted XSS exposure given read-mostly surface).
- **ADR-079** — portal-org-identification (JWT-derived, not subdomain or path).

**Read-model + sync:**
- **ADR-031** — separate-portal-read-model-schema (the original Phase 7 decision: dedicated `portal` schema, event-driven projections, CQRS boundary).
- **ADR-032** — spring-application-events-for-portal (in-process Spring `ApplicationEvent` over SQS/Kafka — chose loose coupling without infra).
- **ADR-078** — portal-read-model-extension (extend the same pattern to invoices + tasks; ADR is explicit about visibility-filter-at-sync-time).
- **ADR-109** — portal-read-model-sync-granularity (hybrid: list views from read-model, transactional-action page lives — for acceptance flow).
- **ADR-253** — portal-surfaces-as-read-model-extensions (Phase 68: trust/retainer/deadlines projected the same way; rejected JPA + live cross-schema).
- **ADR-254** — portal-description-sanitisation (sanitiser at sync time strips matter-internal narrative).
- **ADR-255** — portal-retainer-member-display (whether portal shows member names on retainer consumption — settings-driven enum).
- **ADR-256** — polymorphic-portal-deadline-view (single read-model row type for legal + accounting deadline shapes).
- **ADR-257** — custom-field-portal-visibility-opt-in (custom fields default to internal; opt-in flag exposes to portal).

**App + UI:**
- **ADR-076** — separate-portal-app (`portal/` is its own Next.js bundle, not a route group in `frontend/`).
- **ADR-252** — portal-slim-left-rail-nav.

**Notifications:**
- **ADR-258** — portal-notification-no-double-send-rule (Phase 68 channel handles new events only; existing events keep existing wiring).

## Key flows

- **`50-flows/portal-magic-link-to-task-completion.md`** *(to be written)* — the canonical end-to-end: staff creates an `InformationRequest` → backend emails the portal contact via `PortalEmailNotificationChannel` (or the legacy path per ADR-258) → contact clicks magic link → `/portal/auth/exchange` mints JWT → contact lands on `/requests/[id]`, uploads each item, submits → submit POST writes through to the staff-side `InformationRequest` → staff sees completed items in the staff frontend; portal `portal_request_items` is updated AFTER_COMMIT.
- **`50-flows/proposal-sent-to-accepted.md`** (cross-link from `proposals-acceptance.md`) — proposal-sent path uses the portal as the acceptance UI; backend mints a per-proposal token; acceptance read uses the live `AcceptanceRequest` row (ADR-109 hybrid).

## Open questions / known fragility

1. **Read-model schema (ADR-031) vs read-model extension (ADR-078) — one schema or many.** ADR-031 (Phase 7) established a single dedicated `portal` schema with event-driven projections, accessed via a separate `JdbcClient`/datasource. ADR-078 (Phase later) extended the same schema with new tables for invoices and tasks. ADR-253 (Phase 68) extends it again for trust/retainer/deadlines. The cumulative effect is a 12+-table portal schema that now contains nearly as many entities as some of the staff-side modules. The pattern is sound but the **schema is not lightweight any more**, and resync (`PortalResyncController` + `PortalResyncService`) must rebuild a non-trivial subset of the firm. Worth noting: the bounded-contexts entry says "tenant schema, populated via listeners" `→ ../10-bounded-contexts.md:328` while the `portal` schema is global per ADR-031/253. **The bounded-contexts wording lags reality** — actual placement is the global `portal` schema with `search_path portal, public`. Track at next architecture pass.

2. **localStorage JWT vs cookie (ADR-077) revisit when destructive operations grow.** ADR-077's exposure analysis assumes the portal is "read-mostly except comment posting." Today's surface includes comment post + proposal accept/decline + acceptance accept + info-request item upload + info-request submit + payment-link refresh redirects. None of these are catastrophic, but the trajectory is one-way — every phase adds more write surface. ADR-077 explicitly says: "If the portal adds destructive operations in a future phase, migration to HTTP-only cookies should be prioritized." That migration has not been triggered, and the heuristic for triggering it is not formalised. Candidate: define a "destructive-op count" budget in ADR-077 follow-up.

3. **Token revocation when a `PortalContact` is offboarded.** When staff flips a `PortalContact` to `SUSPENDED` or `ARCHIVED`, **existing portal JWTs remain valid for the remainder of their TTL**. The 1h cap bounds exposure but does not eliminate it: a contact suspended at 14:00 with a JWT minted at 13:55 has access until ~14:55. There is no JWT denylist or session-invalidation table. For most tenants this is acceptable (1h is short, suspend events are rare); for legal/financial verticals where access boundaries are sharper it may not be. No ADR addresses this. Decisions deferred:
   - Add an `invalidated_at` column to `portal_contacts` and check it in `CustomerAuthFilter`?
   - Maintain a `revoked_jwt_jti` table and cross-check on every request?
   - Tighten the JWT TTL?
   The current de-facto answer is "the 1h TTL is the revocation mechanism." Document it explicitly or fix it.

4. **Read-model sync failure semantics (ADR-109 — granularity, not failure).** ADR-109 covers *what* to sync (hybrid) but not *what happens when a sync handler fails*. `@TransactionalEventListener(AFTER_COMMIT)` runs out-of-band: an exception inside the handler does **not** roll back the source transaction (it cannot — the source is already committed). The result is a divergent read-model row (e.g., invoice committed as SENT staff-side, `portal_invoices` not updated). There is no documented retry or repair path beyond `PortalResyncController` (a manual full-rebuild). Worth a short follow-up ADR or §6 addendum: outbox pattern? per-handler retry? alerting? `PortalEventHandler` currently logs and swallows.

5. **Duplicated terminology map.** `portal/lib/terminology-map.ts` and `frontend/lib/terminology-map.ts` ship the same content in two bundles `→ ../_discovery/A6-cross-cutting.md:218-220`. The backend exposes `terminologyKey` only (the *map* lives in the frontend). Renaming a profile key requires changes in both files. Cross-link to `60-verticals/` — the duplicated map is acknowledged there as "elegant (no backend change to retitle a UI label) but fragile (two-file edit on rename)."

6. **`/api/portal/acceptance/*` prefix asymmetry.** Every other portal route is under `/portal/*`; the acceptance pre-auth endpoints sit under `/api/portal/acceptance/*` `→ ../_discovery/A3-portal-gateway-map.md:131`. Historic: the acceptance flow predates the rest of the portal surface. Naming inconsistency, not a bug, but a contributor reading the route table will notice. Refactor candidate at the next portal API consolidation pass.

7. **`portal/` package vs `customerbackend/` package split.** Auth + branding + activity live in `backend/.../portal/`; the read-model controllers + sync services + projections live in `backend/.../customerbackend/`. Both are documented in this single bounded-context module. The split is historical — `customerbackend/` was added in Phase 7 when ADR-031 introduced the dedicated read-model layer; the original `portal/` package kept the auth primitives. Renaming/merging would touch dozens of imports for no functional gain, but the two-package shape is a routine source of confusion for new contributors. Flag, don't fix.
