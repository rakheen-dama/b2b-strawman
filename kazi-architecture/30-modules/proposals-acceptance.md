# Proposals & Acceptance

**Bounded context:** see [`10-bounded-contexts.md` § proposals-acceptance](../10-bounded-contexts.md).

## Purpose

Two related — but independently usable — aggregates ride this module: a **sales-pipeline `Proposal`** (pre-engagement quote/letter sent to a customer with a fee model and an expiry) and a **lightweight `AcceptanceRequest`** (token-gated e-sign for any `GeneratedDocument`, producing a hashed-PDF Certificate of Acceptance). A proposal flows to acceptance when sent, but `AcceptanceRequest` is **not** subordinate to `Proposal` — any generated document (engagement letter, mandate variation, FICA acknowledgement, NDA) can be wrapped in an acceptance request directly. Both aggregates carry a status enum, an `expiresAt`, and a dedicated hourly expiry processor (`@Scheduled fixedDelay = 1h`) that fires terminal events at the deadline.

The flow is staff-side authoring → portal-contact-side review/accept on the customer portal — the acceptance page is **pre-auth** (no portal session needed; ADR-107) so an emailed link works straight from the inbox until `expiresAt`.

UI label for `Proposal` is **"Engagement Letter"** in both `accounting-za` and `legal-za` profiles `→ frontend/lib/terminology-map.ts:29,57`. Backend always says `Proposal`.

## Entities owned

- `Proposal` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/Proposal.java:31` — aggregate root (ADR-124). Columns: `proposalNumber`, `title`, `customerId`, `portalContactId`, `status`, `feeModel`, fixed/hourly/retainer/contingency fee fields (`Proposal.java:60-89`), `expiresAt`, `content` (Tiptap JSONB). Lifecycle javadoc at `:24-27`. `TERMINAL_STATUSES = {ACCEPTED, DECLINED, EXPIRED}` `→ Proposal.java:33-34`.
- `ProposalStatus` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalStatus.java:4` — `DRAFT | SENT | ACCEPTED | DECLINED | EXPIRED`.
- `FeeModel` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/FeeModel.java:4` — `FIXED | HOURLY | RETAINER | CONTINGENCY` (ADR-129). Per-proposal pricing model; `CONTINGENCY` carries LPC Rule 59 / Contingency Fees Act 66 of 1997 fields `→ Proposal.java:80-89`. Glossary `→ glossary.md:128`.
- `ProposalMilestone` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalMilestone.java` — milestone schedule attached to a proposal.
- `ProposalTeamMember` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalTeamMember.java` — proposed team list (display-only on the portal proposal page).
- `ProposalCounter` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalCounter.java` — per-tenant numbering counter (ADR-128).
- `AcceptanceRequest` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceRequest.java:22` — aggregate root for the e-sign flow. Columns: `generatedDocumentId`, `portalContactId`, `customerId`, `status`, `requestToken` (unique, indexed; ADR-107), `sentAt/viewedAt/acceptedAt/expiresAt/revokedAt`, `acceptorName/acceptorIpAddress/acceptorUserAgent` (proof bundle), `certificateS3Key/certificateFileName` (ADR-108), `sentByMemberId`, `revokedByMemberId`, `reminderCount`, `lastRemindedAt`. Status sets at `:24-28`.
- `AcceptanceStatus` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceStatus.java:4` — `PENDING | SENT | VIEWED | ACCEPTED | EXPIRED | REVOKED`. Distinct enum from `ProposalStatus` `→ glossary.md:30`.
- `AcceptanceSubmission` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceSubmission.java` — value record bundling typed-name + IP + user-agent passed from the portal accept endpoint into `AcceptanceService`.

The acceptance request **anchors** the certificate artefact (ADR-108) — the PDF lives at `{tenantSchema}/certificates/{requestId}/certificate.pdf` and the `certificateS3Key` field is the only pointer (no `GeneratedDocument` row is created for it; see ADR-108 §Decision).

## REST surface

`ProposalController` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalController.java` (~10 endpoints, base `/api/proposals`, anchored against `_discovery/A1-backend-map.md:397`):

| Method + path | Capability gate | Notes |
|---|---|---|
| `GET /` | `INVOICING` (module gate; see Cross-cutting) | List with status/customer filters. |
| `POST /` | `INVOICING` | Create draft. |
| `GET /{id}` | `INVOICING` | Read. |
| `PUT /{id}` | `INVOICING` | Edit (DRAFT only — terminal-status enforcement on entity). |
| `DELETE /{id}` | `INVOICING` | Delete (DRAFT only). |
| `POST /{id}/send` | `INVOICING` | DRAFT→SENT; emits `ProposalSentEvent` (AFTER_COMMIT email handler). |
| `POST /{id}/accept` | `INVOICING` | Manual mark-accepted (staff-side fallback if accepted offline). |
| `POST /{id}/decline` | `INVOICING` | Manual mark-declined. |
| `POST /{id}/sync-portal` | `INVOICING` | Re-sync to portal read-model (idempotent). |
| `GET /{id}/pdf` | `INVOICING` | Render proposal PDF. |

A1 names this controller's role as "CRUD + send + accept/decline + portal sync" with ~10 endpoints `→ _discovery/A1-backend-map.md:397`.

`AcceptanceController` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceController.java` (~8 endpoints, base `/api/acceptance-requests`, `_discovery/A1-backend-map.md:398`):

| Method + path | Capability gate | Notes |
|---|---|---|
| `GET /` | (member access) | List with status/customer filters. |
| `POST /` | (member capability) | Create + send acceptance request for a `GeneratedDocument`. |
| `GET /{id}` | | Read. |
| `POST /{id}/send` | | Mark SENT, emit `AcceptanceRequestSentEvent`. |
| `POST /{id}/revoke` | | Mark REVOKED, emit `AcceptanceRequestRevokedEvent`. |
| `POST /{id}/remind` | | Resend email; bumps `reminderCount` + `lastRemindedAt`. |
| `GET /{id}/certificate` | | Stream/redirect to certificate PDF (ADR-108 download endpoint). |
| `GET /{id}/audit` | | Acceptance audit trail (proof bundle). |

### Portal surface (token-gated, **pre-auth**)

The acceptance flow is the only major staff-side feature with a **pre-authentication** entry point. ADR-107 chose a separate `requestToken` (independent of `MagicLinkToken`) so the URL works for the full 30-day acceptance window without requiring a portal session.

| Path | Verb | Source |
|---|---|---|
| `/api/portal/acceptance/{token}` | GET | `portal/lib/api/acceptance.ts:11` `→ A3-portal-gateway-map.md:91` |
| `/api/portal/acceptance/{token}/accept` | POST | `portal/lib/api/acceptance.ts:24` `→ A3-portal-gateway-map.md:92` |
| `/api/portal/acceptance/{token}/pdf` | GET (iframe src) | `portal/lib/api/acceptance.ts:44` `→ A3-portal-gateway-map.md:93` |

Backed by `PortalAcceptanceController` `→ backend/.../acceptance/PortalAcceptanceController.java` per `_discovery/A1-backend-map.md:426` ("GET pending, GET /{token}, POST /{token}/accept"). VIEWED and ACCEPTED transitions are recorded here.

The portal-proposal flow uses the **portal session** (magic-link auth), not a token — proposals are listed on `/proposals` and accepted/declined from `/proposals/[id]`:

| Path | Verb | Source |
|---|---|---|
| `/portal/api/proposals` | GET | `portal/app/proposals/page.tsx:32` `→ A3-portal-gateway-map.md:112` |
| `/portal/api/proposals/{id}` | GET | `portal/app/proposals/[id]/page.tsx:68` `→ A3-portal-gateway-map.md:113` |
| `/portal/api/proposals/{id}/accept` | POST | `portal/app/proposals/[id]/page.tsx:89` `→ A3-portal-gateway-map.md:114` |
| `/portal/api/proposals/{id}/decline` | POST | `portal/app/proposals/[id]/page.tsx:110` `→ A3-portal-gateway-map.md:115` |

Backed by `PortalProposalController` `→ backend/.../proposal/PortalProposalController.java` per `_discovery/A1-backend-map.md:427` ("GET list, GET /{token}, POST /{token}/accept, POST /{token}/decline"). A1's "/{token}" wording is a holdover — proposals are addressed by `id` in the portal surface, with the magic-link-derived `PortalContact` providing identity.

The two surfaces use different addressing: **proposals = portal session + id**; **acceptance = pre-auth + token**. This asymmetry is deliberate (ADR-107 §Rationale).

## Frontend pages / components

Anchors against `_discovery/A2-frontend-map.md` and `_discovery/A3-portal-gateway-map.md`:

**Staff app (Next.js, port 3000):**
- `frontend/app/(app)/org/[slug]/proposals/page.tsx` — proposal list + summary cards. **Capability-gated** to `INVOICING` `→ A2-frontend-map.md:138-139`.
- `frontend/app/(app)/org/[slug]/settings/acceptance/page.tsx` — settings page for acceptance-request expiry defaults `→ A2-frontend-map.md:222-223`.
- `frontend/lib/types/proposal.ts:11` — `ProposalResponse` `→ A2-frontend-map.md:357`.
- The proposal detail/editor surface (Tiptap content editor, fee-model picker, send dialog) lives under `frontend/app/(app)/org/[slug]/proposals/[id]/...` and `frontend/components/proposals/...` (anchored via the page list above; the detail subtree is not separately enumerated in A2).

**Portal app (Next.js, port 3002 — separate bundle):**
- `portal/app/proposals/page.tsx` — proposal list (actionable vs past) `→ A3-portal-gateway-map.md:42`.
- `portal/app/proposals/[id]/page.tsx` — proposal detail with fee, Tiptap HTML, Accept/Decline `→ A3-portal-gateway-map.md:43`.
- `portal/app/accept/[token]/acceptance-page.tsx` — **token-gated** acceptance flow (no session required) `→ A3-portal-gateway-map.md:34, :288`.
- `portal/lib/api/acceptance.ts` — public acceptance API client `→ A3-portal-gateway-map.md:287`.
- `portal/components/pending-acceptances-list.tsx` — surfaces pending acceptances on the portal home/projects page; calls `/portal/acceptance-requests/pending` `→ A3-portal-gateway-map.md:120`. Note A3 §line 46: there is no standalone `/acceptance` portal page — pending acceptances are surfaced via this list component.

## Domain events

All members of the sealed `DomainEvent` hierarchy `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java`.

**Emitted from this module** (per `_discovery/A1-backend-map.md:460-461`):

- `ProposalSentEvent` — `ProposalService` on DRAFT→SENT.
- `ProposalAcceptedEvent` — `ProposalService` (and via `ProposalAcceptedEventHandler` for downstream orchestration; the file `proposal/ProposalAcceptedEvent.java` is module-local). Note: A1's per-package event table only lists `ProposalSentEvent` and `ProposalExpiredEvent` for `proposal/`; `ProposalAcceptedEvent` is present in the source tree (`backend/.../proposal/ProposalAcceptedEvent.java`) and has its own handler — see Open Questions for the orchestration cross-link.
- `ProposalExpiredEvent` — `ProposalExpiryProcessor` when SENT proposals pass `expiresAt`.
- `ProposalOrchestrationFailedEvent` — `ProposalOrchestrationService` when the proposal-accepted → orchestration cascade fails (compensating-action signal). Source: `backend/.../proposal/ProposalOrchestrationFailedEvent.java`. Anchored in this module's package; see ADR-125 for the transactional boundary.
- `AcceptanceRequestSentEvent` — `AcceptanceService` on PENDING/created→SENT.
- `AcceptanceRequestViewedEvent` — `AcceptanceService` on first portal `GET /api/portal/acceptance/{token}`.
- `AcceptanceRequestAcceptedEvent` — `AcceptanceService` on POST `/accept`. Carries the proof bundle (acceptorName, IP, user-agent) and triggers certificate generation.
- `AcceptanceRequestRevokedEvent` — `AcceptanceService` on staff-side revoke.
- `AcceptanceRequestExpiredEvent` — `AcceptanceExpiryProcessor` when SENT/VIEWED requests pass `expiresAt`.

A1 §line 460-461 enumerates the seven canonical proposal+acceptance events; the eighth (`ProposalAcceptedEvent`) is module-local glue.

**Consumed:**

- `notification/NotificationService` consumes `AcceptanceRequestAcceptedEvent` (in-app + email confirmation to the firm) per `_discovery/A1-backend-map.md:475`.
- `portal` read-model listeners consume `AcceptanceRequestSentEvent`, `AcceptanceRequestAcceptedEvent`, `ProposalSentEvent`, `ProposalExpiredEvent` per `_discovery/A1-backend-map.md:476` — sync into portal read-model tables (ADR-127, ADR-109).
- `automation/AutomationEventListener` consumes `PROPOSAL_SENT` and `DOCUMENT_ACCEPTED` triggers per `glossary.md:272` (TriggerType enum) — the rule engine reacts to these without a transactional phase per A6 §6.
- `ProposalSentEmailHandler`, `ProposalAcceptedEventHandler`, `ProposalPortalSyncEventHandler`, `ProposalExpiredEventHandler` are module-local handlers in the `proposal/` package that subscribe within the AFTER_COMMIT phase for irreversible side-effects (email send, portal sync). Same pattern as the rest of the bus per `_discovery/A6-cross-cutting.md` §6.

## Cross-cutting touchpoints

### Capability gate: `INVOICING` for proposals

Anchored at `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`. The proposals page is module/capability-gated to `INVOICING` per `_discovery/A2-frontend-map.md:139` ("Proposal list + summary cards (INVOICING cap)"). All `ProposalController` write paths carry the same capability annotation. Acceptance-request endpoints are member-level (no `INVOICING` requirement) because acceptance can be triggered against any document, not only proposal output.

### AFTER_COMMIT phase for outbound side-effects

Email send (proposal sent, acceptance request sent, acceptance accepted) and portal read-model sync run on `@TransactionalEventListener(phase = AFTER_COMMIT)` per `_discovery/A6-cross-cutting.md` §6 — the universal pattern for irreversible operations. Same reasoning as invoicing: a rolled-back transaction must not produce an email. Listeners: `ProposalSentEmailHandler`, `AcceptanceNotificationService`, the portal sync handlers above.

### Audit on every transition

Every status transition (`Proposal.markSent/markAccepted/markDeclined/markExpired`, `AcceptanceRequest` equivalents) writes an audit event in-transaction via `AuditService.log(...)`. Same belt-and-braces pattern as `customer-lifecycle` and `projects` — see `_discovery/A6-cross-cutting.md` §3.

### Two expiry processors, both `@Scheduled fixedDelay = 1h`

Anchored at `_discovery/A1-backend-map.md:492-493` and `:500`:

- `proposal/ProposalExpiryProcessor#processExpired` (`→ ProposalExpiryProcessor.java:25`) — expires `SENT` proposals past `expiresAt`; publishes `ProposalExpiredEvent`.
- `acceptance/AcceptanceExpiryProcessor#processExpired` (`→ AcceptanceExpiryProcessor.java:14`) — expires `SENT`/`VIEWED` acceptance requests past `expiresAt`; publishes `AcceptanceRequestExpiredEvent`.

Both run via the standard tenant-iteration pattern (`TenantScopedRunner.forEachTenant`) and operate independently. Hourly cadence is generous: there is no hard SLA on the precise expiry minute — a few minutes' lag is acceptable for both flows.

### Acceptance-token strategy (ADR-107) makes the pre-auth surface safe

The portal acceptance page (`portal/app/accept/[token]/...`) is the only major customer-facing route that does **not** require a session. Safety rests on:
- 256 bits of entropy (`SecureRandom`, 32 bytes hex-encoded) — same approach as `MagicLinkToken` (ADR-107 §Consequences).
- Unique-indexed `request_token` column for fast lookup and to prevent enumeration.
- Rate limiting on the lookup endpoint (called out as required in ADR-107 §Negative).
- Token authenticates **access to one specific acceptance action only** — not a portal session, not access to other documents (ADR-107 §Pros). Sharing the URL grants acceptance ability to whoever clicks; this is documented and accepted (ADR-107 §Negative).

### Certificate generation (ADR-108) is in-transaction with the accept event

On `POST /api/portal/acceptance/{token}/accept`: `AcceptanceCertificateService` `→ backend/.../acceptance/AcceptanceCertificateService.java` downloads the original PDF from S3, computes its SHA-256, renders the Certificate of Acceptance via `PdfRenderingService` + `templates/certificates/certificate-of-acceptance.html` (ADR-108 §Neutral), and writes both `certificateS3Key` and `certificateFileName` onto `AcceptanceRequest`. The original PDF is never modified — that constraint is decisive in the ADR (Option 3 was rejected because appending invalidates the embedded hash). The accept transaction commits, then `AcceptanceRequestAcceptedEvent` fires AFTER_COMMIT for email + portal sync — see ADR-125 (acceptance-orchestration-transaction-boundary).

### Numbering (ADR-128)

`ProposalNumberService` + `ProposalCounter` + `ProposalCounterRepository` produce per-tenant sequential numbers. The counter is a separate row to avoid a `SELECT MAX(...) FOR UPDATE` against `proposals`.

## Vertical specifics

- **Terminology overlay.** Both `accounting-za` and `legal-za` profiles rename UI label `Proposal` → "Engagement Letter" (and `Proposals` → "Engagement Letters"; lowercase variants too) `→ frontend/lib/terminology-map.ts:29-32, :57-60`. Glossary `→ glossary.md:120, :222`. Backend code/tables/events all say `Proposal`. Notifications and emails render via the frontend's `TerminologyProvider` so the customer-facing copy is correctly localised per profile.
- **`FeeModel.CONTINGENCY` is realistic only in `legal-za`.** The contingency fields (`contingencyPercent`, `contingencyCapPercent`, `contingencyDescription` `→ Proposal.java:80-89`) carry LPC Rule 59 / Contingency Fees Act 66 of 1997 semantics. The enum value is universally available, but its compliance shape only fits the legal vertical.
- **No vertical gating on the controllers themselves.** Proposals are a core-SaaS feature `→ _discovery/A1-backend-map.md:582` ("Core-SaaS shared"); only the `INVOICING` capability is required. The "Engagement Letter" rename is purely a frontend terminology concern.

## Active ADRs

Anchored against the proposal/engagement cluster in `90-adr-index.md` (lines 184-188 and adjacent entries):

- **ADR-107** (acceptance-token-strategy) — separate `requestToken` independent of `MagicLinkToken`; aligns expiry with the acceptance request's own `expiresAt` (typically 30 days). Single-step pre-auth flow. `→ adr/ADR-107-acceptance-token-strategy.md`.
- **ADR-108** (certificate-storage-and-integrity) — certificate stored as a separate S3 object referenced by `AcceptanceRequest.certificateS3Key` rather than as a `GeneratedDocument` row or appended page. Original PDF immutable to keep SHA-256 hash valid. `→ adr/ADR-108-certificate-storage-and-integrity.md`.
- **ADR-124** (proposal-storage-model) — `Proposal` aggregate shape, Tiptap JSONB content column, fee-model column union. `→ adr/ADR-124-proposal-storage-model.md`.
- **ADR-125** (acceptance-orchestration-transaction-boundary) — accept event commits before downstream orchestration (certificate, notifications, portal sync) fires AFTER_COMMIT; failure paths use `ProposalOrchestrationFailedEvent`. `→ adr/ADR-125-acceptance-orchestration-transaction-boundary.md`.
- **ADR-127** (portal-proposal-rendering) — portal renders Tiptap content as HTML on the proposal detail page. `→ adr/ADR-127-portal-proposal-rendering.md`.
- **ADR-128** (proposal-numbering-strategy) — per-tenant sequential numbering via dedicated counter row. `→ adr/ADR-128-proposal-numbering-strategy.md`.
- **ADR-129** (fee-model-architecture) — `FeeModel` enum + per-model column union on `Proposal` (no separate fee-table). `→ adr/ADR-129-fee-model-architecture.md`.
- **ADR-251** (acceptance-eligible-template-manifest-flag) — manifest flag on `DocumentTemplate` declaring whether a generated document from that template is acceptance-eligible. Filters the "create acceptance request" picker. `→ adr/ADR-251-acceptance-eligible-template-manifest-flag.md`.

## Key flows

- `50-flows/proposal-to-engagement-to-billing.md` — staff drafts proposal → sends → portal contact reviews/accepts → optional auto-creation of `Project`/engagement → engagement billing. This is the canonical end-to-end path for the module.

## Open questions / known fragility

- **Proposal → Project conversion semantics.** Does a `ProposalAcceptedEvent` automatically create a `Project`? `ProposalOrchestrationService` exists in this package and is the obvious convertor candidate, but the trigger surface in `glossary.md:272` lists `PROPOSAL_SENT` and `DOCUMENT_ACCEPTED` (not `PROPOSAL_ACCEPTED`) — suggesting the orchestration may run via the rule engine rather than as a hard-coded conversion. ADR-125 governs the transactional boundary but does not pin down whether project creation is automatic, opt-in via automation rule, or staff-triggered. Verify in `architecture/phase32-proposal-engagement-pipeline.md` before relying on either reading.
- **Acceptance certificate chain of custody.** The proof bundle (acceptorName + IP + user-agent + UTC timestamp) is captured in the accept-event transaction, then a SHA-256 of the original PDF is computed at certificate-rendering time. If S3 returns a different byte sequence than what was originally generated (e.g. silent key collision, lifecycle policy mutation, manual edit), the certificate's hash will reflect the post-mutation file — there is no second-source-of-truth check. ADR-108 §Neutral notes the hash is "computed at acceptance time" but does not specify whether the hash is also re-validated on later certificate downloads. Worth pinning down.
- **Two expiry processors run independently — does ordering matter?** `ProposalExpiryProcessor` and `AcceptanceExpiryProcessor` are both `@Scheduled fixedDelay = 1h` per `_discovery/A1-backend-map.md:492-493` and run on the standard tenant-iteration pattern. There is no defined ordering. If a proposal's acceptance request expires first (say, acceptance was given a tighter 14-day window than the proposal's 30-day expiry), the proposal stays SENT until *its* expiry processor reaps it. The reverse — proposal expired but acceptance request still SENT/VIEWED — is the more interesting one: should proposal expiry cascade-revoke any open acceptance request? Today the answer is no (each processor only updates its own aggregate). Flagged for product clarification.
- **ProposalAcceptedEvent presence vs A1 event table.** A1 §line 461 lists only `ProposalSentEvent` and `ProposalExpiredEvent` for the `proposal/` package, but the source tree contains `ProposalAcceptedEvent.java` and `ProposalAcceptedEventHandler.java`. Either A1's per-package event table is incomplete or `ProposalAcceptedEvent` is intentionally **not** in the sealed-`DomainEvent` permits list (i.e. a private signal between `ProposalService` and its handler, not a bus event). Verify against `event/DomainEvent.java` permits.
- **`PortalProposalController` is documented with `/{token}` paths in A1 but the portal app addresses proposals by id.** `_discovery/A1-backend-map.md:427` describes `GET /{token}, POST /{token}/accept, POST /{token}/decline`; `portal/app/proposals/[id]/page.tsx:68` calls `/portal/api/proposals/{id}` (and `/accept`, `/decline`) `→ _discovery/A3-portal-gateway-map.md:113-115`. Either A1's path-template is stale, or the controller has both id-based and token-based variants and only the id-based one is wired through the portal. Worth confirming in source — proposals having an alternate token surface analogous to acceptance would be a meaningful divergence from ADR-107's "one pre-auth pattern, one aggregate" design.
